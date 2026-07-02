import { Ionicons } from '@expo/vector-icons';
import { View } from 'react-native';
import { Property } from '@/lib/api';
import { Card, Text, formatCedis } from '@/components/ui';
import { colors, spacing } from '@/theme';

export function PropertyCard({ property, onPress }: { property: Property; onPress?: () => void }) {
  return (
    <Card onPress={onPress}>
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: spacing.lg }}>
        <View
          style={{
            width: 48,
            height: 48,
            borderRadius: 12,
            backgroundColor: colors.tealTint,
            alignItems: 'center',
            justifyContent: 'center',
          }}
        >
          <Ionicons name={property.type === 'COMMERCIAL' ? 'storefront' : 'home'} size={24} color={colors.primary} />
        </View>
        <View style={{ flex: 1, gap: 2 }}>
          <Text variant="headlineSm" numberOfLines={1}>
            {property.name}
          </Text>
          <Text variant="labelMd" color={colors.textMuted}>
            {property.locality ?? property.type} · {property.assetCount ?? 0} assets
          </Text>
        </View>
        <View style={{ alignItems: 'flex-end', gap: 2 }}>
          {typeof property.totalEstimatedValue === 'number' ? (
            <Text variant="labelMd" weight="bold" color={colors.primary}>
              {formatCedis(property.totalEstimatedValue)}
            </Text>
          ) : null}
          <Ionicons name="chevron-forward" size={18} color={colors.textMuted} />
        </View>
      </View>
    </Card>
  );
}
