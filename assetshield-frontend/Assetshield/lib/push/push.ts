import Constants from 'expo-constants';
import { Platform } from 'react-native';
import { notificationsApi } from '@/lib/api';

/**
 * FCM device-token lifecycle (handoff §7). Register on login and on token
 * rotation; delete on logout.
 *
 * Expo Go (SDK 53+) removed push support, and merely importing expo-notifications
 * there throws. So we (a) detect Expo Go and no-op, and (b) only ever import
 * expo-notifications via a guarded dynamic import — never at module top level.
 * In dev (FCM_MODE=log) push doesn't land on a device anyway; the in-app inbox
 * (GET /users/me/notifications) drives notifications. Use a development build to
 * exercise real push.
 */

const isExpoGo = Constants.appOwnership === 'expo';

let currentToken: string | null = null;

function platform(): 'ANDROID' | 'IOS' {
  return Platform.OS === 'ios' ? 'IOS' : 'ANDROID';
}

async function getDeviceToken(): Promise<string | null> {
  if (isExpoGo) return null;
  try {
    const Notifications = await import('expo-notifications');
    const perm = await Notifications.getPermissionsAsync();
    let granted = perm.granted;
    if (!granted) {
      const req = await Notifications.requestPermissionsAsync();
      granted = req.granted;
    }
    if (!granted) return null;
    const token = await Notifications.getDevicePushTokenAsync(); // native FCM token on Android
    return typeof token.data === 'string' ? token.data : null;
  } catch {
    return null;
  }
}

/** Register the device token with the backend. Safe to call on every login. */
export async function registerPushToken(): Promise<void> {
  if (isExpoGo) return;
  const token = await getDeviceToken();
  if (!token) return;
  currentToken = token;
  try {
    await notificationsApi.registerDeviceToken({ fcmToken: token, platform: platform() });
  } catch {
    // non-fatal — inbox still works
  }
}

/** Listen for FCM token rotation and re-register. Returns an unsubscribe fn. */
export function listenForTokenRotation(): () => void {
  if (isExpoGo) return () => {};
  let remove = () => {};
  import('expo-notifications')
    .then((Notifications) => {
      const sub = Notifications.addPushTokenListener((token) => {
        if (typeof token.data === 'string') {
          currentToken = token.data;
          notificationsApi.registerDeviceToken({ fcmToken: token.data, platform: platform() }).catch(() => {});
        }
      });
      remove = () => sub.remove();
    })
    .catch(() => {});
  return () => remove();
}

/** Remove the device token on logout. */
export async function unregisterPushToken(): Promise<void> {
  if (isExpoGo) return;
  const token = currentToken ?? (await getDeviceToken());
  if (!token) return;
  try {
    await notificationsApi.deleteDeviceToken(token);
  } catch {
    // ignore
  } finally {
    currentToken = null;
  }
}
