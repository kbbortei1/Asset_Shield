import { Stack } from 'expo-router';
import { View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { OfflineBanner } from '@/components/ui';
import { useOffline } from '@/lib/offline/OfflineProvider';
import { useNotificationDeepLink } from '@/lib/push/useNotificationDeepLink';
import { colors } from '@/theme';

export default function AppLayout() {
  const { isOnline, pending } = useOffline();
  useNotificationDeepLink();
  return (
    <View style={{ flex: 1, backgroundColor: colors.background }}>
      {!isOnline ? (
        <SafeAreaView edges={['top']} style={{ backgroundColor: colors.primary }}>
          <OfflineBanner visible pending={pending} />
        </SafeAreaView>
      ) : null}
      <View style={{ flex: 1 }}>
        {/* Each Screen/ListScreen paints its own opaque themed base + backdrop, so
            the scene is opaque and transitions never reveal the screen underneath. */}
        <Stack screenOptions={{ headerShown: false, contentStyle: { backgroundColor: colors.background } }} />
      </View>
    </View>
  );
}
