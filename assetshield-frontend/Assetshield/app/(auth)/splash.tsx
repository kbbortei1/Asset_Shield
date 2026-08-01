import * as SecureStore from 'expo-secure-store';
import { router } from 'expo-router';
import { useEffect, useRef } from 'react';
import { Animated, Pressable } from 'react-native';
import { Logo } from '@/components/brand/Logo';
import { Text } from '@/components/ui';
import { colors, spacing } from '@/theme';

const SEEN_KEY = 'as_seen_splash';

/**
 * Branded intro splash shown on cold start before Welcome. Full beat on the
 * very first launch; a quick ~1.2s brand flash on every return visit (repeat
 * users shouldn't wait). Tap anywhere to skip.
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

    let t: ReturnType<typeof setTimeout>;
    SecureStore.getItemAsync(SEEN_KEY)
      .then((seen) => {
        t = setTimeout(advance, seen ? 1200 : 3400);
        if (!seen) SecureStore.setItemAsync(SEEN_KEY, '1').catch(() => {});
      })
      .catch(() => {
        t = setTimeout(advance, 3400);
      });
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
          Protect what matters.
        </Text>
      </Animated.View>
    </Pressable>
  );
}
