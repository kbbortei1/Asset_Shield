import Constants from 'expo-constants';

/**
 * Base URL for the gateway (only :8080 is published; everything lives under /api/v1).
 *
 * Resolution order:
 *  1. EXPO_PUBLIC_API_URL if set — explicit override for emulators (10.0.2.2),
 *     staging or production.
 *  2. Otherwise, in dev, derive the host from the Expo dev server that served
 *     this bundle (Constants.expoConfig.hostUri, e.g. "192.168.196.59:8081") and
 *     swap in the gateway port. This means the app ALWAYS reaches the same machine
 *     the phone already reached for Metro — so a DHCP/LAN-IP change can never
 *     silently break login again.
 *  3. Fallback to localhost (simulator / web).
 */
const GATEWAY_PORT = 8080;
const API_PREFIX = '/api/v1';
const FALLBACK = `http://localhost:${GATEWAY_PORT}${API_PREFIX}`;

/** Pull the LAN IP the device used to load the bundle from Expo's dev host. */
function apiUrlFromExpoHost(): string | undefined {
  const hostUri =
    Constants.expoConfig?.hostUri ??
    // Older/Expo Go shape: "192.168.196.59:8081" (may carry an "exp://" scheme).
    (Constants as { expoGoConfig?: { debuggerHost?: string } }).expoGoConfig?.debuggerHost;
  const host = hostUri?.replace(/^\w+:\/\//, '').split(':')[0];
  if (!host) return undefined;
  return `http://${host}:${GATEWAY_PORT}${API_PREFIX}`;
}

export const API_BASE_URL = (process.env.EXPO_PUBLIC_API_URL ?? apiUrlFromExpoHost() ?? FALLBACK).replace(/\/$/, '');

/** Scheme+host+port, without the /api/v1 path — used to resolve relative media URLs. */
export const API_ORIGIN = API_BASE_URL.replace(/\/api\/v\d+$/, '');

/**
 * Resolve a media/download URL from the API. With local storage the backend
 * returns a RELATIVE path (e.g. "/api/v1/public/damage-files/<token>"), which
 * WebBrowser and <Image> can't use — prefix it with the API origin. Absolute
 * URLs (e.g. Supabase presigned https links) are returned unchanged.
 */
export function resolveMediaUrl(url?: string | null): string | undefined {
  if (!url) return undefined;
  if (/^https?:\/\//i.test(url)) return url;
  return API_ORIGIN + (url.startsWith('/') ? url : `/${url}`);
}

/** Signed image/PDF URLs expire ~15 min — never cache them long-term (handoff §10). */
export const SIGNED_URL_TTL_MS = 15 * 60 * 1000;
