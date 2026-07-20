import { Ionicons } from '@expo/vector-icons';
import { View } from 'react-native';
import { Property } from '@/lib/api';
import { Card, Text, formatCedis } from '@/components/ui';
import { colors, radius, spacing } from '@/theme';

const STALE_DAYS = 90;

/**
 * Documentation status turns the property list into a to-do:
 * no assets → "Add photos"; last capture > 90 days → "Needs update";
 * otherwise → "Documented".
 */
function docStatus(p: Property): { label: string; bg: string } {
  if (!p.assetCount) return { label: 'Add photos', bg: colors.cta };
  if (p.lastDocumentedAt) {
    const ageDays = (Date.now() - new Date(p.lastDocumentedAt).getTime()) / 86_400_000;
    if (ageDays > STALE_DAYS) return { label: 'Needs update', bg: colors.warning };
  }
  return { label: 'Documented', bg: colors.success };
}

export function PropertyCard({ property, onPress }: { property: Property; onPress?: () => void }) {
  const status = docStatus(property);
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
        <View style={{ flex: 1, gap: 3 }}>
          <Text variant="headlineSm" numberOfLines={1}>
            {property.name}
          </Text>
          <Text variant="labelMd" color={colors.textMuted}>
            {property.locality ?? property.type} · {property.assetCount ?? 0} assets
          </Text>
          <View style={{ flexDirection: 'row' }}>
            <View style={{ backgroundColor: status.bg, borderRadius: radius.sm, paddingHorizontal: 6, paddingVertical: 2 }}>
              <Text variant="labelMd" color={colors.white} style={{ fontSize: 10 }} weight="semibold">
                {status.label}
              </Text>
            </View>
          </View>
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
