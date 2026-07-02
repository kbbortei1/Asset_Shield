import { useEffect, useRef } from 'react';
import { Animated } from 'react-native';

/**
 * Subtle staggered entrance (fade + slide-up) for list items. Wrap each row and
 * pass its index; the delay staggers by index. Capped so long lists don't lag.
 */
export function AnimatedItem({ index = 0, children }: { index?: number; children: React.ReactNode }) {
  const opacity = useRef(new Animated.Value(0)).current;
  const translateY = useRef(new Animated.Value(10)).current;

  useEffect(() => {
    const delay = Math.min(index, 8) * 55;
    Animated.parallel([
      Animated.timing(opacity, { toValue: 1, duration: 320, delay, useNativeDriver: true }),
      Animated.timing(translateY, { toValue: 0, duration: 320, delay, useNativeDriver: true }),
    ]).start();
  }, [opacity, translateY, index]);

  return <Animated.View style={{ opacity, transform: [{ translateY }] }}>{children}</Animated.View>;
}
