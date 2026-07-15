/**
 * Offline queue replay semantics (handoff §5) + dead-lettering:
 * - success and duplicate-hash both dequeue
 * - connectivity loss stops the flush WITHOUT penalising items
 * - real server rejections count toward MAX_ATTEMPTS, then dead-letter
 */
import { failedCount, flushQueue, pendingCount, retryFailed } from '../queue';
import { queueDb } from '../db';
import { api } from '@/lib/api';

const MAX = 5; // mirrors db.MAX_ATTEMPTS

jest.mock('@/lib/api', () => {
  class ApiError extends Error {
    code: string;
    httpStatus: number;
    constructor(code: string, httpStatus: number) {
      super(code);
      this.code = code;
      this.httpStatus = httpStatus;
    }
  }
  return {
    ApiError,
    api: { upload: jest.fn() },
    isApiError: (e: unknown): e is InstanceType<typeof ApiError> => e instanceof ApiError,
    isDuplicateHash: (e: unknown) =>
      e instanceof ApiError && (e.code === 'DUPLICATE_ASSET_HASH' || e.code === 'DUPLICATE_PHOTO_HASH'),
  };
});

type Row = { id: string; attempts: number; lastError?: string | null; [k: string]: unknown };

jest.mock('../db', () => {
  const MAX_ATTEMPTS = 5;
  let rows: Row[] = [];
  return {
    MAX_ATTEMPTS,
    __reset: () => {
      rows = [];
    },
    queueDb: {
      insert: jest.fn(async (item: Row) => {
        rows.push({ ...item, attempts: 0 });
      }),
      all: jest.fn(async () => rows.filter((r) => r.attempts < MAX_ATTEMPTS)),
      count: jest.fn(async () => rows.filter((r) => r.attempts < MAX_ATTEMPTS).length),
      dead: jest.fn(async () => rows.filter((r) => r.attempts >= MAX_ATTEMPTS)),
      deadCount: jest.fn(async () => rows.filter((r) => r.attempts >= MAX_ATTEMPTS).length),
      reviveDead: jest.fn(async () => {
        rows.forEach((r) => {
          if (r.attempts >= MAX_ATTEMPTS) r.attempts = 0;
        });
      }),
      remove: jest.fn(async (id: string) => {
        rows = rows.filter((r) => r.id !== id);
      }),
      markFailure: jest.fn(async (id: string, error: string) => {
        const r = rows.find((x) => x.id === id);
        if (r) {
          r.attempts += 1;
          r.lastError = error;
        }
      }),
    },
  };
});

// eslint-disable-next-line @typescript-eslint/no-require-imports
const { __reset } = require('../db') as { __reset: () => void };
const uploadMock = api.upload as jest.Mock;
// eslint-disable-next-line @typescript-eslint/no-require-imports
const { ApiError } = require('@/lib/api') as { ApiError: new (code: string, status: number) => Error };

const item = (id: string): Row => ({
  id,
  kind: 'asset',
  endpoint: '/e',
  fileUri: `file:///${id}.jpg`,
  fileName: `${id}.jpg`,
  mimeType: 'image/jpeg',
  metadata: '{}',
  label: id,
  createdAt: new Date().toISOString(),
  attempts: 0,
});

beforeEach(() => {
  jest.clearAllMocks();
  __reset();
  if (typeof globalThis.FormData === 'undefined') {
    // minimal polyfill for the node test env
    (globalThis as Record<string, unknown>).FormData = class {
      append() {}
    };
  }
});

async function seed(...ids: string[]) {
  for (const id of ids) await queueDb.insert(item(id) as never);
}

describe('flushQueue', () => {
  it('uploads and dequeues successful items', async () => {
    await seed('a', 'b');
    uploadMock.mockResolvedValue({});
    const res = await flushQueue();
    expect(res).toEqual({ synced: 2, remaining: 0 });
  });

  it('treats duplicate-hash as success (already stored server-side)', async () => {
    await seed('a');
    uploadMock.mockRejectedValueOnce(new ApiError('DUPLICATE_ASSET_HASH', 409));
    const res = await flushQueue();
    expect(res).toEqual({ synced: 1, remaining: 0 });
  });

  it('stops on connectivity loss WITHOUT penalising the item', async () => {
    await seed('a', 'b');
    uploadMock.mockRejectedValue(new ApiError('NETWORK_ERROR', 0));
    const res = await flushQueue();
    expect(res.synced).toBe(0);
    expect(res.remaining).toBe(2); // both still pending
    expect(queueDb.markFailure).not.toHaveBeenCalled();
    expect(uploadMock).toHaveBeenCalledTimes(1); // broke after the first
  });

  it('counts real server rejections and dead-letters after MAX_ATTEMPTS', async () => {
    await seed('bad');
    uploadMock.mockRejectedValue(new ApiError('FILE_TOO_LARGE', 413));

    for (let i = 0; i < MAX; i++) await flushQueue();

    expect(await pendingCount()).toBe(0); // no longer retried
    expect(await failedCount()).toBe(1); // dead-lettered, kept for the user
    // subsequent flushes don't touch it
    uploadMock.mockClear();
    await flushQueue();
    expect(uploadMock).not.toHaveBeenCalled();
  });

  it('retryFailed revives dead items and replays them', async () => {
    await seed('bad');
    uploadMock.mockRejectedValue(new ApiError('INTERNAL_ERROR', 500));
    for (let i = 0; i < MAX; i++) await flushQueue();
    expect(await failedCount()).toBe(1);

    uploadMock.mockResolvedValue({}); // server recovered
    const res = await retryFailed();
    expect(res).toEqual({ synced: 1, remaining: 0 });
    expect(await failedCount()).toBe(0);
  });
});
