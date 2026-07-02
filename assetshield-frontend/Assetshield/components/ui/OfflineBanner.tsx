import { Ionicons } from '@expo/vector-icons';
import { View } from 'react-native';
import { colors, spacing } from '@/theme';
import { Text } from './Text';

/**
 * Slim Guardian Teal bar shown below the header when offline (design.md).
 * `pending` shows the unsynced capture count from the offline queue.
 */
export function OfflineBanner({ visible, pending = 0 }: { visible: boolean; pending?: number }) {
  if (!visible) return null;
  return (
    <View
      style={{
        flexDirection: 'row',
        alignItems: 'center',
        gap: spacing.sm,
        backgroundColor: colors.primary,
        paddingHorizontal: spacing.screenPadding,
        paddingVertical: spacing.sm,
      }}
    >
      <Ionicons name="cloud-offline-outline" size={16} color={colors.onPrimary} />
      <Text variant="labelMd" color={colors.onPrimary} style={{ flex: 1 }}>
        Offline{pending > 0 ? ` — ${pending} change${pending === 1 ? '' : 's'} will sync` : ' — changes will sync'} when back online.
      </Text>
    </View>
  );
}
