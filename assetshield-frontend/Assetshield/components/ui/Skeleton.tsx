import { useEffect, useRef } from 'react';
import { Animated, DimensionValue, StyleSheet, View, ViewStyle } from 'react-native';
import { colors, elevation, radius, spacing } from '@/theme';

/** A single pulsing placeholder block. */
export function Skeleton({
  width = '100%',
  height = 16,
  rounded = radius.sm,
  style,
}: {
  width?: DimensionValue;
  height?: number;
  rounded?: number;
  style?: ViewStyle;
}) {
  const o = useRef(new Animated.Value(0.5)).current;
  useEffect(() => {
    const loop = Animated.loop(
      Animated.sequence([
        Animated.timing(o, { toValue: 1, duration: 750, useNativeDriver: true }),
        Animated.timing(o, { toValue: 0.5, duration: 750, useNativeDriver: true }),
      ]),
    );
    loop.start();
    return () => loop.stop();
  }, [o]);
  return <Animated.View style={[{ width, height, borderRadius: rounded, backgroundColor: colors.border, opacity: o }, style]} />;
}

/** A list-row shaped skeleton card. */
export function SkeletonCard() {
  return (
    <View
      style={[
        {
          backgroundColor: colors.card,
          borderRadius: radius.lg,
          padding: spacing.lg,
          flexDirection: 'row',
          alignItems: 'center',
          gap: spacing.lg,
          borderWidth: StyleSheet.hairlineWidth,
          borderColor: colors.border,
        },
        elevation.card,
      ]}
    >
      <Skeleton width={48} height={48} rounded={12} />
      <View style={{ flex: 1, gap: spacing.sm }}>
        <Skeleton width="70%" height={16} />
        <Skeleton width="45%" height={12} />
      </View>
    </View>
  );
}

/** A stack of skeleton cards for list loading states. */
export function ListSkeleton({ count = 5, hero = false }: { count?: number; hero?: boolean }) {
  return (
    <View style={{ gap: spacing.lg }}>
      {hero ? <Skeleton height={132} rounded={radius.lg} /> : null}
      {Array.from({ length: count }).map((_, i) => (
        <SkeletonCard key={i} />
      ))}
    </View>
  );
}
