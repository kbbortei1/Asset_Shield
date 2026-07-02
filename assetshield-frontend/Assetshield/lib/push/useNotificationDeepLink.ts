import Constants from 'expo-constants';
import { router } from 'expo-router';
import { useEffect } from 'react';
import { routeForNotification } from './deeplink';

/**
 * Navigate when the user taps a push notification (handoff §7). Guarded for
 * Expo Go (no push support there): we only import expo-notifications via a
 * dynamic import outside Expo Go, so the module is never loaded in Expo Go.
 */
const isExpoGo = Constants.appOwnership === 'expo';

export function useNotificationDeepLink() {
  useEffect(() => {
    if (isExpoGo) return;
    let cleanup = () => {};

    import('expo-notifications')
      .then((Notifications) => {
        const navigate = (response: { notification: { request: { content: { data?: unknown } } } }) => {
          const data = (response.notification.request.content.data ?? {}) as Record<string, string> & { type?: string };
          try {
            router.push(routeForNotification(data.type, data) as never);
          } catch {
            // route may not be ready yet; ignore
          }
        };
        Notifications.getLastNotificationResponseAsync().then((r) => {
          if (r) navigate(r);
        });
        const sub = Notifications.addNotificationResponseReceivedListener(navigate);
        cleanup = () => sub.remove();
      })
      .catch(() => {});

    return () => cleanup();
  }, []);
}
