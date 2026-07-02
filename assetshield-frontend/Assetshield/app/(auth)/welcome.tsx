import { Ionicons } from '@expo/vector-icons';
import { router } from 'expo-router';
import { useRef } from 'react';
import { Animated, useWindowDimensions, View } from 'react-native';
import { Logo } from '@/components/brand/Logo';
import { Button, Screen, Text } from '@/components/ui';
import { colors, radius, spacing } from '@/theme';

type Slide = {
  key: string;
  icon?: keyof typeof Ionicons.glyphMap;
  tint?: string;
  title: string;
  body: string;
  brand?: boolean; // first slide shows the logo instead of an icon
};

/** Swipeable intro shown after the splash, before sign-in. */
const SLIDES: Slide[] = [
  {
    key: 'store',
    brand: true,
    title: 'Your digital safety deposit box',
    body: 'Document what you own — photos, receipts and values — in one secure place, right from your phone.',
  },
  {
    key: 'prove',
    icon: 'shield-checkmark',
    tint: colors.primary,
    title: 'Prove what you lost',
    body: 'Every photo is hashed into tamper-evident evidence, time-stamped and ready to stand behind a claim.',
  },
  {
    key: 'recover',
    icon: 'flash',
    tint: colors.cta,
    title: 'Recover faster',
    body: 'Generate a claim dossier and connect with verified insurers in minutes, not weeks.',
  },
];

export default function Welcome() {
  const { width } = useWindowDimensions();
  const scrollX = useRef(new Animated.Value(0)).current;

  return (
    <Screen scroll={false} padded={false} contentStyle={{ flex: 1, gap: spacing.xl }}>
      <Animated.ScrollView
        horizontal
        pagingEnabled
        showsHorizontalScrollIndicator={false}
        style={{ flex: 1 }}
        scrollEventThrottle={16}
        onScroll={Animated.event([{ nativeEvent: { contentOffset: { x: scrollX } } }], { useNativeDriver: true })}
      >
        {SLIDES.map((s) => (
          <View
            key={s.key}
            style={{ width, alignItems: 'center', justifyContent: 'center', paddingHorizontal: spacing.xl, gap: spacing.xl }}
          >
            {s.brand ? (
              <Logo size={96} />
            ) : (
              <View
                style={{
                  width: 132,
                  height: 132,
                  borderRadius: radius.pill,
                  backgroundColor: colors.tealTint,
                  alignItems: 'center',
                  justifyContent: 'center',
                }}
              >
                <Ionicons name={s.icon!} size={60} color={s.tint} />
              </View>
            )}
            <View style={{ gap: spacing.sm }}>
              <Text variant="headlineLgMobile" align="center">
                {s.title}
              </Text>
              <Text variant="bodyLg" color={colors.textMuted} align="center">
                {s.body}
              </Text>
            </View>
          </View>
        ))}
      </Animated.ScrollView>

      <Dots count={SLIDES.length} width={width} scrollX={scrollX} />

      <View style={{ gap: spacing.md, paddingHorizontal: spacing.screenPadding }}>
        <Button title="Get started" onPress={() => router.push('/(auth)/role' as never)} />
        <Button title="I already have an account" variant="ghost" onPress={() => router.push('/(auth)/login' as never)} />
      </View>
    </Screen>
  );
}

/**
 * Animated page indicator — the active dot stretches into a pill. Uses scaleX
 * (not width) so it can run on the native driver alongside the scroll value;
 * the native animated module doesn't support animating layout props like width.
 */
function Dots({ count, width, scrollX }: { count: number; width: number; scrollX: Animated.Value }) {
  return (
    <View style={{ flexDirection: 'row', justifyContent: 'center', alignItems: 'center', gap: spacing.sm }}>
      {Array.from({ length: count }).map((_, i) => {
        const inputRange = [(i - 1) * width, i * width, (i + 1) * width];
        const scaleX = scrollX.interpolate({ inputRange, outputRange: [1, 3, 1], extrapolate: 'clamp' });
        const opacity = scrollX.interpolate({ inputRange, outputRange: [0.35, 1, 0.35], extrapolate: 'clamp' });
        return (
          <Animated.View
            key={i}
            style={{ width: 8, height: 8, borderRadius: 4, backgroundColor: colors.primary, opacity, transform: [{ scaleX }] }}
          />
        );
      })}
    </View>
  );
}
