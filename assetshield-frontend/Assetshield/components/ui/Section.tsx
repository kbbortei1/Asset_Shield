import { Pressable, View } from 'react-native';
import { colors, spacing } from '@/theme';
import { Text } from './Text';

/** Section title with an optional right-aligned text action. */
export function SectionHeader({
  title,
  actionLabel,
  onAction,
}: {
  title: string;
  actionLabel?: string;
  onAction?: () => void;
}) {
  return (
    <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginTop: spacing.xs }}>
      <Text variant="headlineSm">{title}</Text>
      {actionLabel && onAction ? (
        <Pressable hitSlop={8} onPress={onAction}>
          <Text variant="labelMd" color={colors.primary} weight="semibold">
            {actionLabel}
          </Text>
        </Pressable>
      ) : null}
    </View>
  );
}

/** 1px hairline divider. */
export function Divider({ inset = 0 }: { inset?: number }) {
  return <View style={{ height: 1, backgroundColor: colors.border, marginLeft: inset }} />;
}
