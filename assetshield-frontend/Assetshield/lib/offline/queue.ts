import { api, isApiError, isDuplicateHash } from '@/lib/api';
import { queueDb, QueuedUpload, QueueKind } from './db';

/**
 * Offline upload queue. Captures are enqueued with the hash already computed;
 * on reconnect we replay them. A 409 duplicate-hash on replay means the photo
 * already landed → treat as success and dequeue (handoff §5).
 */

type EnqueueInput = {
  kind: QueueKind;
  endpoint: string;
  fileUri: string;
  fileName: string;
  mimeType: string;
  metadata: object; // includes sha256Hash computed at capture time
  label: string;
};

function newId(): string {
  return `${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
}

export async function enqueueUpload(input: EnqueueInput): Promise<void> {
  await queueDb.insert({
    id: newId(),
    kind: input.kind,
    endpoint: input.endpoint,
    fileUri: input.fileUri,
    fileName: input.fileName,
    mimeType: input.mimeType,
    metadata: JSON.stringify(input.metadata),
    label: input.label,
    createdAt: new Date().toISOString(),
  });
}

export async function pendingCount(): Promise<number> {
  return queueDb.count();
}

export async function listPending(): Promise<QueuedUpload[]> {
  return queueDb.all();
}

/** Uploads that exhausted their retries on real server errors (dead-lettered). */
export async function failedCount(): Promise<number> {
  return queueDb.deadCount();
}

export async function listFailed(): Promise<QueuedUpload[]> {
  return queueDb.dead();
}

/** Reset dead-lettered items for another round of retries, then replay. */
export async function retryFailed(): Promise<FlushResult> {
  await queueDb.reviveDead();
  return flushQueue();
}

/** Permanently discard one dead-lettered upload. */
export async function discardFailed(id: string): Promise<void> {
  await queueDb.remove(id);
}

let flushing = false;

export type FlushResult = { synced: number; remaining: number };

/** Replay all queued uploads. Safe to call repeatedly; single-flight guarded. */
export async function flushQueue(): Promise<FlushResult> {
  if (flushing) return { synced: 0, remaining: await queueDb.count() };
  flushing = true;
  let synced = 0;
  try {
    const items = await queueDb.all();
    for (const item of items) {
      const form = new FormData();
      form.append('file', { uri: item.fileUri, name: item.fileName, type: item.mimeType } as any);
      form.append('metadata', item.metadata);
      try {
        await api.upload(item.endpoint, form);
        await queueDb.remove(item.id);
        synced++;
      } catch (e) {
        if (isDuplicateHash(e)) {
          // already stored server-side — success-equivalent
          await queueDb.remove(item.id);
          synced++;
        } else if (isApiError(e) && e.httpStatus === 0) {
          // Connectivity dropped mid-flush: NOT the item's fault, so don't
          // count it toward dead-lettering. Stop and retry on next reconnect.
          break;
        } else {
          // Real server rejection (validation, too-large, …): counts toward
          // MAX_ATTEMPTS, after which the item is dead-lettered instead of
          // retrying forever on every reconnect.
          await queueDb.markFailure(item.id, isApiError(e) ? e.code : String(e));
        }
      }
    }
  } finally {
    flushing = false;
  }
  return { synced, remaining: await queueDb.count() };
}
