import { useQuery, useQueryClient } from '@tanstack/react-query';
import * as SecureStore from 'expo-secure-store';
import { notificationsApi } from '@/lib/api';

/**
 * Unread notifications, tracked entirely on the device.
 *
 * The backend returns a `readAt` on each notification but never sets it (there
 * is no mark-read endpoint), so per-item read state is not reliable. Instead we
 * remember the moment the user last opened the alerts screen and count anything
 * newer than that. Simple, offline-safe, and it survives a restart because the
 * timestamp lives in SecureStore.
 */
const LAST_SEEN_KEY = 'as_notifications_last_seen';
const LAST_SEEN_QUERY = ['notifications', 'last-seen'];

async function readLastSeen(): Promise<number> {
  const raw = await SecureStore.getItemAsync(LAST_SEEN_KEY);
  const n = raw ? Number(raw) : 0;
  return Number.isFinite(n) ? n : 0;
}

/** Count of notifications newer than the last time the user opened alerts. */
export function useUnreadCount(): number {
  const lastSeen = useQuery({ queryKey: LAST_SEEN_QUERY, queryFn: readLastSeen, staleTime: Infinity });
  const list = useQuery({
    queryKey: ['notifications'],
    queryFn: () => notificationsApi.list({ size: 50 }),
    // Background refresh so the bell badge surfaces new alerts (e.g. a chat
    // message from the other party) within ~half a minute, without a socket.
    refetchInterval: 25_000,
  });

  const since = lastSeen.data ?? 0;
  const items = list.data?.items ?? [];
  return items.reduce((count, n) => (new Date(n.createdAt).getTime() > since ? count + 1 : count), 0);
}

/** Call when the user opens the alerts screen: everything up to now is "seen". */
export function useMarkNotificationsSeen(): () => void {
  const qc = useQueryClient();
  return () => {
    const now = Date.now();
    SecureStore.setItemAsync(LAST_SEEN_KEY, String(now)).catch(() => {});
    qc.setQueryData(LAST_SEEN_QUERY, now);
  };
}
