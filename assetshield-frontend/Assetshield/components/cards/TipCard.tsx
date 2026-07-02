import { Ionicons } from '@expo/vector-icons';
import { View } from 'react-native';
import { Tip } from '@/lib/api';
import { Card, Text } from '@/components/ui';
import { colors, spacing } from '@/theme';

export function TipCard({ tip, onPress }: { tip: Tip; onPress?: () => void }) {
  const read = !!tip.readAt;
  return (
    <Card onPress={onPress}>
      <View style={{ flexDirection: 'row', gap: spacing.md }}>
        <Ionicons name="bulb" size={22} color={colors.cta} />
        <View style={{ flex: 1, gap: 4 }}>
          {tip.category ? (
            <Text variant="labelMd" color={colors.textMuted}>
              {tip.category}
            </Text>
          ) : null}
          <Text variant="bodyMd" weight={read ? 'regular' : 'semibold'}>
            {tip.tipText}
          </Text>
        </View>
        {!read ? <View style={{ width: 8, height: 8, borderRadius: 4, backgroundColor: colors.cta, marginTop: 6 }} /> : null}
      </View>
    </Card>
  );
}
