import { Pressable, StyleSheet, View, ViewProps, ViewStyle } from 'react-native';
import { colors, elevation, radius, spacing } from '@/theme';

export type CardProps = ViewProps & {
  onPress?: () => void;
  padded?: boolean;
  style?: ViewStyle | ViewStyle[];
};

/** Level 1 surface: themed card, 16px radius, soft shadow + hairline border. */
export function Card({ children, onPress, padded = true, style, ...rest }: CardProps) {
  const cardStyle = [
    {
      backgroundColor: colors.card,
      borderRadius: radius.lg,
      padding: padded ? spacing.lg : 0,
      borderWidth: StyleSheet.hairlineWidth,
      borderColor: colors.border,
    },
    elevation.card,
    style,
  ];

  if (onPress) {
    return (
      <Pressable
        onPress={onPress}
        android_ripple={{ color: colors.tealTint, borderless: false }}
        style={({ pressed }) => [cardStyle, pressed && { opacity: 0.92, transform: [{ scale: 0.99 }] }]}
      >
        {children}
      </Pressable>
    );
  }
  return (
    <View style={cardStyle} {...rest}>
      {children}
    </View>
  );
}
