import { Ionicons } from '@expo/vector-icons';
import Constants from 'expo-constants';
import * as ImagePicker from 'expo-image-picker';
import * as Location from 'expo-location';
import { router, useLocalSearchParams } from 'expo-router';
import { useCallback, useEffect, useState } from 'react';
import { View } from 'react-native';
import { Button, Card, Header, Screen, Text } from '@/components/ui';
import { colors, spacing } from '@/theme';

// Expo Go (SDK 53+) removed push support, and merely importing expo-notifications
// there throws. Only ever import it via a guarded dynamic import — never at module
// top level — so loading this screen doesn't crash in Expo Go.
const isExpoGo = Constants.appOwnership === 'expo';

async function requestNotificationPermission(): Promise<boolean> {
  if (isExpoGo) return false;
  try {
    const Notifications = await import('expo-notifications');
    const res = await Notifications.requestPermissionsAsync();
    return res.granted;
  } catch {
    return false;
  }
}

type Granted = { camera: boolean; location: boolean; notifications: boolean };

/**
 * Stitch: "Meet Ama & Permissions". Primes camera/location/notifications before
 * the capture flow. Requests are best-effort; the user can still continue.
 * Rows show live status: in Expo Go the OS dialogs often never appear because
 * Expo Go itself already holds the permission, so the checkmarks are the only
 * visible feedback.
 */
export default function Permissions() {
  const { role } = useLocalSearchParams<{ role?: string }>();
  const [granted, setGranted] = useState<Granted>({ camera: false, location: false, notifications: false });
  const [busy, setBusy] = useState(false);

  const refresh = useCallback(async () => {
    const cam = await ImagePicker.getCameraPermissionsAsync().catch(() => null);
    const loc = await Location.getForegroundPermissionsAsync().catch(() => null);
    setGranted((g) => ({ ...g, camera: !!cam?.granted, location: !!loc?.granted }));
  }, []);

  useEffect(() => {
    refresh();
  }, [refresh]);

  const requestAll = async () => {
    setBusy(true);
    try {
      const cam = await ImagePicker.requestCameraPermissionsAsync().catch(() => null);
      const loc = await Location.requestForegroundPermissionsAsync().catch(() => null);
      const notif = await requestNotificationPermission();
      setGranted({ camera: !!cam?.granted, location: !!loc?.granted, notifications: notif });
    } finally {
      setBusy(false);
    }
    next();
  };

  const next = () => {
    const target = role === 'agent' ? '/(auth)/register-agent' : '/(auth)/register';
    router.replace(target as never);
  };

  return (
    <Screen scroll={false} contentStyle={{ flex: 1 }}>
      <Header />
      <View style={{ gap: spacing.sm }}>
        <Text variant="headlineLgMobile">A few permissions to protect your assets</Text>
        <Text variant="bodyMd" color={colors.textMuted}>
          AssetShield uses these to capture tamper-evident evidence. Nothing is shared without your consent.
        </Text>
      </View>

      <View style={{ gap: spacing.md, marginTop: spacing.lg, flex: 1 }}>
        <PermRow icon="camera" title="Camera" body="Photograph your assets and any damage." granted={granted.camera} />
        <PermRow icon="location" title="Location" body="Geo-tag evidence so it can be paired automatically." granted={granted.location} />
        <PermRow
          icon="notifications"
          title="Notifications"
          body="Get alerts when a dossier is ready or an agent responds."
          granted={granted.notifications}
        />
      </View>

      <View style={{ gap: spacing.md }}>
        <Button title="Allow & continue" loading={busy} onPress={requestAll} />
        <Button title="Maybe later" variant="ghost" onPress={next} />
      </View>
    </Screen>
  );
}

function PermRow({
  icon,
  title,
  body,
  granted,
}: {
  icon: keyof typeof Ionicons.glyphMap;
  title: string;
  body: string;
  granted: boolean;
}) {
  return (
    <Card>
      <View style={{ flexDirection: 'row', gap: spacing.lg, alignItems: 'center' }}>
        <Ionicons name={icon} size={26} color={colors.primary} />
        <View style={{ flex: 1, gap: 2 }}>
          <Text variant="headlineSm">{title}</Text>
          <Text variant="bodyMd" color={colors.textMuted}>
            {body}
          </Text>
        </View>
        {granted ? <Ionicons name="checkmark-circle" size={22} color={colors.success} /> : null}
      </View>
    </Card>
  );
}
