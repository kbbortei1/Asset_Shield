import { Image } from 'expo-image';
import { LinearGradient } from 'expo-linear-gradient';
import { router } from 'expo-router';
import { useRef } from 'react';
import { Animated, Pressable, useWindowDimensions, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Button, Text } from '@/components/ui';
import { spacing } from '@/theme';

const EMBLEM = require('@/assets/images/logo-emblem-clean.png');

/**
 * Launch onboarding: three illustrated slides (store → prove → recover). The
 * artwork is a full-bleed image that fades into the cream base; the wordmark,
 * title, body, dots and buttons are all native so they stay crisp, tappable and
 * translatable. This screen is deliberately locked to the light brand palette so
 * the warm illustrations always sit on matching cream, regardless of theme.
 */
const CREAM = '#F6F5F1';
const INK = '#11252B';
const MUTED = '#5C6B70';
const TEAL = '#0E5A52';
const GOLD = '#F4A93C';

type Slide = { key: string; image: number; title: string; body: string };

const SLIDES: Slide[] = [
  {
    key: 'store',
    image: require('@/assets/images/onboarding-1.png'),
    title: 'Your digital safety deposit box',
    body: 'Document what you own: photos, receipts and values, all in one secure place on your phone.',
  },
  {
    key: 'prove',
    image: require('@/assets/images/onboarding-2.png'),
    title: 'Prove what you lost',
    body: 'Every photo is hashed into tamper-evident evidence, time-stamped and ready to stand behind a claim.',
  },
  {
    key: 'recover',
    image: require('@/assets/images/onboarding-3.png'),
    title: 'Recover faster',
    body: 'Generate a claim dossier and connect with verified insurers in minutes, not weeks.',
  },
];

export default function Welcome() {
  const { width } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const scrollX = useRef(new Animated.Value(0)).current;

  return (
    <View style={{ flex: 1, backgroundColor: CREAM }}>
      <Animated.ScrollView
        horizontal
        pagingEnabled
        showsHorizontalScrollIndicator={false}
        style={{ flex: 1 }}
        scrollEventThrottle={16}
        onScroll={Animated.event([{ nativeEvent: { contentOffset: { x: scrollX } } }], { useNativeDriver: true })}
      >
        {SLIDES.map((s) => (
          <View key={s.key} style={{ width }}>
            {/* full-bleed illustration, fading into the cream base */}
            <View style={{ flex: 5 }}>
              <Image source={s.image} style={{ width: '100%', height: '100%' }} contentFit="cover" contentPosition="top" />
              <LinearGradient
                colors={['rgba(246,245,241,0)', CREAM]}
                style={{ position: 'absolute', left: 0, right: 0, bottom: 0, height: '30%' }}
              />
            </View>

            {/* native brand lockup + copy */}
            <View style={{ flex: 4, paddingHorizontal: spacing.xl, gap: spacing.md }}>
              <BrandLockup />
              <Text variant="headlineLg" align="center" color={INK} style={{ fontSize: 30, lineHeight: 38 }}>
                {s.title}
              </Text>
              <Text variant="bodyMd" align="center" color={MUTED}>
                {s.body}
              </Text>
            </View>
          </View>
        ))}
      </Animated.ScrollView>

      {/* skip — sits over the top of the artwork */}
      <Pressable
        accessibilityRole="button"
        accessibilityLabel="Skip introduction"
        hitSlop={12}
        onPress={() => router.replace('/(auth)/role' as never)}
        style={{ position: 'absolute', top: insets.top + spacing.sm, right: spacing.xl, padding: spacing.xs }}
      >
        <Text variant="labelMd" weight="semibold" color={MUTED}>
          Skip
        </Text>
      </Pressable>

      {/* shared footer: dots + actions */}
      <View style={{ paddingHorizontal: spacing.xl, paddingBottom: insets.bottom + spacing.lg, gap: spacing.md }}>
        <Dots count={SLIDES.length} width={width} scrollX={scrollX} />
        <Button title="Get started" onPress={() => router.push('/(auth)/role' as never)} />
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="I already have an account"
          onPress={() => router.push('/(auth)/login' as never)}
          style={{ alignItems: 'center', paddingVertical: spacing.sm }}
        >
          <Text variant="bodyMd" weight="semibold" color={TEAL}>
            I already have an account
          </Text>
        </Pressable>
      </View>
    </View>
  );
}

/** Emblem + wordmark + tagline, centred — the same lockup on every slide. */
function BrandLockup() {
  return (
    <View style={{ alignItems: 'center', gap: 2 }}>
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: spacing.sm }}>
        <Image source={EMBLEM} style={{ width: 30, height: 30 }} contentFit="contain" />
        <Text variant="headlineSm" color={TEAL}>
          AssetShield
        </Text>
        <Text variant="labelMd" weight="semibold" color={GOLD} style={{ fontSize: 11 }}>
          GH
        </Text>
      </View>
      <Text variant="labelMd" color={GOLD} style={{ letterSpacing: 1 }}>
        Protect what matters.
      </Text>
    </View>
  );
}

/**
 * Animated page indicator — the active dot stretches into a pill. Uses scaleX
 * (not width) so it runs on the native driver alongside the scroll value.
 */
function Dots({ count, width, scrollX }: { count: number; width: number; scrollX: Animated.Value }) {
  return (
    <View style={{ flexDirection: 'row', justifyContent: 'center', alignItems: 'center', gap: spacing.sm }}>
      {Array.from({ length: count }).map((_, i) => {
        const inputRange = [(i - 1) * width, i * width, (i + 1) * width];
        const scaleX = scrollX.interpolate({ inputRange, outputRange: [1, 3, 1], extrapolate: 'clamp' });
        const opacity = scrollX.interpolate({ inputRange, outputRange: [0.3, 1, 0.3], extrapolate: 'clamp' });
        return (
          <Animated.View
            key={i}
            style={{ width: 8, height: 8, borderRadius: 4, backgroundColor: TEAL, opacity, transform: [{ scaleX }] }}
          />
        );
      })}
    </View>
  );
}
