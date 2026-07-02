import { API_BASE_URL } from './config';
import { ApiError } from './errors';
import { clearTokens, getAccessToken, getRefreshToken, setTokens } from './tokens';
import { Envelope, ErrorData } from './types';

/**
 * Single typed API client. Unwraps the { status, data, message } envelope,
 * normalizes errors to the catalogue, and implements the documented
 * 401-retry-once-after-refresh interceptor with single-flight refresh (§2).
 */

let onSessionExpired: (() => void) | null = null;

/** Registered by the auth provider to force re-login when the session dies. */
export function setSessionExpiredHandler(fn: (() => void) | null): void {
  onSessionExpired = fn;
}

let refreshing: Promise<string> | null = null;

async function doRefresh(): Promise<string> {
  const refreshToken = await getRefreshToken();
  if (!refreshToken) throw new ApiError({ code: 'REFRESH_INVALID', httpStatus: 401 });

  const res = await fetch(`${API_BASE_URL}/auth/refresh`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refreshToken }),
  });
  const body = (await res.json().catch(() => null)) as Envelope<TokenResponse> | null;

  if (!res.ok || !body || body.status === 'error') {
    const code = (body?.data as unknown as ErrorData)?.errorCode ?? 'REFRESH_INVALID';
    await clearTokens();
    throw new ApiError({ code, httpStatus: res.status });
  }
  // Refresh tokens are rotated on every refresh — persist the new pair immediately.
  await setTokens({ accessToken: body.data.accessToken, refreshToken: body.data.refreshToken });
  return body.data.accessToken;
}

type TokenResponse = { accessToken: string; refreshToken: string };

export type RequestOptions = {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  body?: unknown;
  query?: Record<string, string | number | boolean | undefined | null>;
  /** send Authorization header (default true) */
  auth?: boolean;
  /** multipart upload — do NOT set Content-Type, let fetch set the boundary (§4) */
  form?: FormData;
  signal?: AbortSignal;
};

async function request<T>(path: string, opts: RequestOptions = {}, retry = true): Promise<T> {
  const { method = 'GET', body, query, auth = true, form, signal } = opts;
  const url = API_BASE_URL + path + buildQuery(query);

  const headers: Record<string, string> = {};
  if (auth) {
    const access = await getAccessToken();
    if (access) headers.Authorization = `Bearer ${access}`;
  }

  let payload: BodyInit | undefined;
  if (form) {
    payload = form as unknown as BodyInit; // multipart: boundary set by fetch
  } else if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
    payload = JSON.stringify(body);
  }

  // Fail fast on an unreachable host (e.g. a stale LAN IP after DHCP change)
  // instead of hanging on the OS TCP timeout. Uploads get a longer budget.
  const controller = new AbortController();
  const timeoutMs = form ? 60_000 : 15_000;
  const timeout = setTimeout(() => controller.abort(), timeoutMs);
  if (signal) {
    if (signal.aborted) controller.abort();
    else signal.addEventListener('abort', () => controller.abort());
  }

  let res: Response;
  try {
    res = await fetch(url, { method, headers, body: payload, signal: controller.signal });
  } catch {
    throw new ApiError({ code: 'NETWORK_ERROR', httpStatus: 0 });
  } finally {
    clearTimeout(timeout);
  }

  if (res.status === 204) return undefined as T;

  const envelope = (await res.json().catch(() => null)) as Envelope<unknown> | null;

  if (res.ok && envelope?.status !== 'error') {
    return (envelope ? envelope.data : undefined) as T;
  }

  const errData = envelope?.data as ErrorData | undefined;
  const code = errData?.errorCode ?? mapHttpToCode(res.status);

  if (res.status === 401 && auth && retry) {
    if (code === 'TOKEN_EXPIRED') {
      try {
        refreshing ??= doRefresh(); // single-flight; concurrent 401s share it
        await refreshing;
        return request<T>(path, opts, false); // retry exactly once
      } catch (refreshErr) {
        await clearTokens();
        onSessionExpired?.();
        throw refreshErr instanceof ApiError ? refreshErr : new ApiError({ code: 'REFRESH_INVALID', httpStatus: 401 });
      } finally {
        refreshing = null;
      }
    }
    if (code === 'REFRESH_REUSED' || code === 'TOKEN_INVALID' || code === 'REFRESH_INVALID' || code === 'REFRESH_EXPIRED') {
      await clearTokens();
      onSessionExpired?.();
    }
  }

  throw new ApiError({
    code,
    httpStatus: res.status,
    fieldErrors: errData?.fieldErrors,
    serverMessage: envelope?.message,
  });
}

function buildQuery(query?: RequestOptions['query']): string {
  if (!query) return '';
  const parts = Object.entries(query)
    .filter(([, v]) => v !== undefined && v !== null)
    .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(String(v))}`);
  return parts.length ? `?${parts.join('&')}` : '';
}

function mapHttpToCode(status: number): string {
  switch (status) {
    case 400:
      return 'VALIDATION_FAILED';
    case 401:
      return 'TOKEN_INVALID';
    case 402:
      return 'PAYMENT_REQUIRED';
    case 403:
      return 'FORBIDDEN';
    case 404:
      return 'RESOURCE_NOT_FOUND';
    case 409:
      return 'ALREADY_RESPONDED';
    case 413:
      return 'FILE_TOO_LARGE';
    case 415:
      return 'UNSUPPORTED_MEDIA_TYPE';
    case 429:
      return 'RATE_LIMITED';
    default:
      return 'INTERNAL_ERROR';
  }
}

export const api = {
  get: <T>(path: string, opts?: Omit<RequestOptions, 'method' | 'body' | 'form'>) =>
    request<T>(path, { ...opts, method: 'GET' }),
  post: <T>(path: string, body?: unknown, opts?: Omit<RequestOptions, 'method' | 'body'>) =>
    request<T>(path, { ...opts, method: 'POST', body }),
  put: <T>(path: string, body?: unknown, opts?: Omit<RequestOptions, 'method' | 'body'>) =>
    request<T>(path, { ...opts, method: 'PUT', body }),
  del: <T>(path: string, body?: unknown, opts?: Omit<RequestOptions, 'method' | 'body'>) =>
    request<T>(path, { ...opts, method: 'DELETE', body }),
  /** multipart upload (file + metadata parts). */
  upload: <T>(path: string, form: FormData, opts?: Omit<RequestOptions, 'method' | 'form' | 'body'>) =>
    request<T>(path, { ...opts, method: 'POST', form }),
};
