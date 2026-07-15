/**
 * Tests the documented 401-retry-once-after-refresh interceptor (handoff §2):
 * unwrapping, error mapping, single-flight refresh, and session-expiry.
 */
import { api, setSessionExpiredHandler } from '../client';
import { API_BASE_URL } from '../config';
import { ApiError } from '../errors';
import { clearTokens, getAccessToken, getRefreshToken, setTokens } from '../tokens';

jest.mock('../tokens', () => ({
  getAccessToken: jest.fn(),
  getRefreshToken: jest.fn(),
  setTokens: jest.fn(),
  clearTokens: jest.fn(),
}));

const mockGetAccess = getAccessToken as jest.Mock;
const mockGetRefresh = getRefreshToken as jest.Mock;
const mockSetTokens = setTokens as jest.Mock;
const mockClearTokens = clearTokens as jest.Mock;

const fetchMock = jest.fn();
global.fetch = fetchMock as unknown as typeof fetch;

function res(status: number, body: unknown) {
  return { ok: status >= 200 && status < 300, status, json: async () => body };
}
const ok = (data: unknown) => res(200, { status: 'success', data, message: null });
const err = (status: number, errorCode: string) =>
  res(status, { status: 'error', data: { errorCode }, message: errorCode });

beforeEach(() => {
  jest.clearAllMocks();
  mockGetAccess.mockResolvedValue('access-old');
  mockGetRefresh.mockResolvedValue('refresh-1');
});

afterEach(() => setSessionExpiredHandler(null));

describe('api client', () => {
  it('unwraps the success envelope and sends the bearer token', async () => {
    fetchMock.mockResolvedValueOnce(ok({ id: 'p1' }));
    const data = await api.get<{ id: string }>('/properties/p1');
    expect(data).toEqual({ id: 'p1' });
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe(`${API_BASE_URL}/properties/p1`);
    expect(init.headers.Authorization).toBe('Bearer access-old');
  });

  it('maps a raw 404 (no envelope) to RESOURCE_NOT_FOUND', async () => {
    fetchMock.mockResolvedValueOnce(res(404, null));
    await expect(api.get('/nope')).rejects.toMatchObject({ code: 'RESOURCE_NOT_FOUND', httpStatus: 404 });
  });

  it('maps fetch rejection to NETWORK_ERROR with httpStatus 0', async () => {
    fetchMock.mockRejectedValueOnce(new TypeError('Network request failed'));
    await expect(api.get('/anything')).rejects.toMatchObject({ code: 'NETWORK_ERROR', httpStatus: 0 });
  });

  it('on TOKEN_EXPIRED: refreshes, persists rotated tokens, retries exactly once', async () => {
    fetchMock
      .mockResolvedValueOnce(err(401, 'TOKEN_EXPIRED')) // original request
      .mockResolvedValueOnce(ok({ accessToken: 'access-new', refreshToken: 'refresh-2' })) // refresh
      .mockResolvedValueOnce(ok({ me: true })); // retried request
    mockGetAccess.mockResolvedValueOnce('access-old').mockResolvedValue('access-new');

    const data = await api.get('/users/me');
    expect(data).toEqual({ me: true });
    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect(fetchMock.mock.calls[1][0]).toBe(`${API_BASE_URL}/auth/refresh`);
    expect(mockSetTokens).toHaveBeenCalledWith({ accessToken: 'access-new', refreshToken: 'refresh-2' });
    expect(fetchMock.mock.calls[2][1].headers.Authorization).toBe('Bearer access-new');
  });

  it('when refresh fails: clears tokens, fires session-expired, rethrows', async () => {
    const onExpired = jest.fn();
    setSessionExpiredHandler(onExpired);
    fetchMock
      .mockResolvedValueOnce(err(401, 'TOKEN_EXPIRED'))
      .mockResolvedValueOnce(err(401, 'REFRESH_EXPIRED'));

    await expect(api.get('/users/me')).rejects.toBeInstanceOf(ApiError);
    expect(mockClearTokens).toHaveBeenCalled();
    expect(onExpired).toHaveBeenCalled();
    expect(fetchMock).toHaveBeenCalledTimes(2); // no retry after failed refresh
  });

  it('single-flight: concurrent 401s share ONE refresh call', async () => {
    fetchMock.mockImplementation(async (url: string, init: RequestInit) => {
      if (url.endsWith('/auth/refresh')) return ok({ accessToken: 'access-new', refreshToken: 'refresh-2' });
      const auth = (init.headers as Record<string, string>).Authorization;
      return auth === 'Bearer access-new' ? ok({ fine: true }) : err(401, 'TOKEN_EXPIRED');
    });
    mockGetAccess.mockImplementation(async () =>
      mockSetTokens.mock.calls.length > 0 ? 'access-new' : 'access-old',
    );

    const [a, b] = await Promise.all([api.get('/one'), api.get('/two')]);
    expect(a).toEqual({ fine: true });
    expect(b).toEqual({ fine: true });
    const refreshCalls = fetchMock.mock.calls.filter(([u]) => String(u).endsWith('/auth/refresh'));
    expect(refreshCalls).toHaveLength(1);
  });

  it('fatal 401 codes clear the session without retrying', async () => {
    const onExpired = jest.fn();
    setSessionExpiredHandler(onExpired);
    fetchMock.mockResolvedValueOnce(err(401, 'REFRESH_REUSED'));

    await expect(api.get('/users/me')).rejects.toMatchObject({ code: 'REFRESH_REUSED' });
    expect(mockClearTokens).toHaveBeenCalled();
    expect(onExpired).toHaveBeenCalled();
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
});
