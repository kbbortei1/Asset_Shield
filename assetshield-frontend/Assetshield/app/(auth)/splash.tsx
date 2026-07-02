import { router } from 'expo-router';
import { useEffect, useRef } from 'react';
import { Animated, Pressable } from 'react-native';
import { Logo } from '@/components/brand/Logo';
import { Text } from '@/components/ui';
import { colors, spacing } from '@/theme';

/**
 * Branded intro splash shown on cold start before Welcome. Fades the logo in and
 * auto-advances after ~2s; tap anywhere to skip.
 */
export default function Splash() {
  const opacity = useRef(new Animated.Value(0)).current;
  const scale = useRef(new Animated.Value(0.86)).current;

  const advance = () => router.replace('/(auth)/welcome' as never);

  useEffect(() => {
    Animated.parallel([
      Animated.timing(opacity, { toValue: 1, duration: 700, useNativeDriver: true }),
      // gentle zoom-in so the emblem reads clearly
      Animated.timing(scale, { toValue: 1, duration: 1400, useNativeDriver: true }),
    ]).start();
    const t = setTimeout(advance, 3400);
    return () => clearTimeout(t);
  }, [opacity, scale]);

  return (
    <Pressable
      onPress={advance}
      style={{ flex: 1, backgroundColor: colors.background, alignItems: 'center', justifyContent: 'center' }}
    >
      <Animated.View style={{ opacity, transform: [{ scale }], alignItems: 'center', gap: spacing.xl }}>
        <Logo size={200} />
        <Text variant="bodyMd" color={colors.textMuted} align="center">
          Protecting what you&apos;ve built
        </Text>
      </Animated.View>
    </Pressable>
  );
}
