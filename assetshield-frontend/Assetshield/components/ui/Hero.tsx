import { Ionicons } from '@expo/vector-icons';
import { LinearGradient } from 'expo-linear-gradient';
import { Pressable, View, ViewStyle } from 'react-native';
import { colors, elevation, radius, spacing } from '@/theme';

export type HeroProps = {
  children: React.ReactNode;
  onPress?: () => void;
  colors?: [string, string];
  style?: ViewStyle;
};

/**
 * Gradient hero surface — used for the primary "protected value" / status cards.
 * Adds depth over a flat fill. Defaults to the Guardian Teal gradient.
 */
export function Hero({ children, onPress, colors: c, style }: HeroProps) {
  const gradient = c ?? [colors.primary, colors.primaryDeep];
  const inner = (
    <LinearGradient
      colors={gradient}
      start={{ x: 0, y: 0 }}
      end={{ x: 1, y: 1 }}
      style={[{ borderRadius: radius.lg, padding: spacing.lg, overflow: 'hidden' }, elevation.card, style]}
    >
      <Ionicons
        name="shield-checkmark"
        size={150}
        color="rgba(255,255,255,0.06)"
        style={{ position: 'absolute', right: -26, bottom: -34 }}
      />
      {children}
    </LinearGradient>
  );
  if (onPress) {
    return (
      <Pressable onPress={onPress} style={({ pressed }) => (pressed ? { opacity: 0.92 } : null)}>
        {inner}
      </Pressable>
    );
  }
  return <View>{inner}</View>;
}
