import { Ionicons } from '@expo/vector-icons';
import { ActivityIndicator, Pressable, View } from 'react-native';
import { LocationFix } from '@/lib/media/capture';
import { colors, spacing } from '@/theme';
import { Card } from './Card';
import { Text } from './Text';

export type LocationConfirmProps = {
  /** undefined = still fetching; {} (no coords) = unavailable */
  fix: LocationFix | undefined;
  onRefresh: () => void;
};

/**
 * Shows WHERE the evidence will be geo-tagged before upload: reverse-geocoded
 * place, coordinates and accuracy. Pairing suggestions depend on this fix, so
 * making it visible (and refreshable) prevents silent bad geo-tags.
 * A full map preview needs a development build (react-native-maps is blank in
 * Expo Go) — this is the key-free confirm step until then.
 */
export function LocationConfirm({ fix, onRefresh }: LocationConfirmProps) {
  const loading = fix === undefined;
  const has = !!fix && typeof fix.gpsLat === 'number' && typeof fix.gpsLng === 'number';

  return (
    <Card style={{ backgroundColor: colors.tealTint }}>
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: spacing.md }}>
        <Ionicons name={has ? 'location' : 'location-outline'} size={22} color={has ? colors.primary : colors.textMuted} />
        <View style={{ flex: 1, gap: 2 }}>
          {loading ? (
            <Text variant="labelMd" color={colors.primary}>
              Getting a location fix…
            </Text>
          ) : has ? (
            <>
              <Text variant="bodyMd" weight="semibold" color={colors.primary}>
                {fix.label ?? 'Location captured'}
              </Text>
              <Text variant="labelMd" color={colors.primary}>
                {fix.gpsLat!.toFixed(5)}, {fix.gpsLng!.toFixed(5)}
                {typeof fix.accuracy === 'number' ? ` · ±${Math.round(fix.accuracy)}m` : ''}
              </Text>
            </>
          ) : (
            <>
              <Text variant="bodyMd" weight="semibold" color={colors.textMuted}>
                Location unavailable
              </Text>
              <Text variant="labelMd" color={colors.textMuted}>
                The photo will be saved without a geo-tag, so it can't be auto-paired.
              </Text>
            </>
          )}
        </View>
        {loading ? (
          <ActivityIndicator size="small" color={colors.primary} />
        ) : (
          <Pressable accessibilityRole="button" accessibilityLabel="Refresh location" hitSlop={10} onPress={onRefresh}>
            <Ionicons name="refresh" size={20} color={colors.primary} />
          </Pressable>
        )}
      </View>
    </Card>
  );
}
