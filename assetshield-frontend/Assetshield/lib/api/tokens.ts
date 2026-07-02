import * as SecureStore from 'expo-secure-store';

/**
 * Token storage. Both tokens live in expo-secure-store (never AsyncStorage).
 * The access token is also cached in memory for request speed; the refresh
 * token is read from the store only when refreshing (handoff §2).
 */
const ACCESS_KEY = 'as_access_token';
const REFRESH_KEY = 'as_refresh_token';

let accessTokenCache: string | null = null;
let loaded = false;

export type TokenPair = { accessToken: string; refreshToken: string };

/** Restore the access token into memory on cold start. */
export async function loadTokens(): Promise<void> {
  accessTokenCache = await SecureStore.getItemAsync(ACCESS_KEY);
  loaded = true;
}

export async function getAccessToken(): Promise<string | null> {
  if (!loaded) await loadTokens();
  return accessTokenCache;
}

export async function getRefreshToken(): Promise<string | null> {
  return SecureStore.getItemAsync(REFRESH_KEY);
}

export async function setTokens({ accessToken, refreshToken }: TokenPair): Promise<void> {
  accessTokenCache = accessToken;
  loaded = true;
  await Promise.all([
    SecureStore.setItemAsync(ACCESS_KEY, accessToken),
    SecureStore.setItemAsync(REFRESH_KEY, refreshToken),
  ]);
}

export async function clearTokens(): Promise<void> {
  accessTokenCache = null;
  loaded = true;
  await Promise.all([SecureStore.deleteItemAsync(ACCESS_KEY), SecureStore.deleteItemAsync(REFRESH_KEY)]);
}

export async function hasSession(): Promise<boolean> {
  return (await getRefreshToken()) != null;
}
