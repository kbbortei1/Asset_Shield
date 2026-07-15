import { useIsFocused } from '@react-navigation/native';
import { useEffect, useRef, useState } from 'react';
import { Animated, StyleSheet, View } from 'react-native';
import { useTheme } from '@/lib/theme/ThemeProvider';
import { palettes } from '@/theme';

const IMAGES = [
  require('@/assets/images/background1.jpg'),
  require('@/assets/images/background2.jpg'),
  require('@/assets/images/background3.jpg'),
  require('@/assets/images/background4.jpg'),
];

const ROTATE_MS = 30_000;

/**
 * Faint rotating photo backdrop. Owns its OWN theme-correct base color (read
 * reactively from the theme, not the mutable `colors` object), so the field is
 * always the right shade — dark in dark/gold, warm-grey in light — with the
 * photo dimmed on top. Rotates every 30s with a soft fade-in. pointerEvents=none
 * so it never blocks touches.
 */
export function AppBackground() {
  const { theme } = useTheme();
  const base = palettes[theme].background;
  const target = theme === 'light' ? 0.1 : 0.16;

  const [index, setIndex] = useState(0);
  const opacity = useRef(new Animated.Value(0)).current;
  // Every Screen mounts its own backdrop; only the focused one should tick,
  // otherwise each screen in a deep stack keeps its own rotation timer alive.
  const focused = useIsFocused();

  useEffect(() => {
    if (!focused) return;
    const t = setInterval(() => setIndex((i) => (i + 1) % IMAGES.length), ROTATE_MS);
    return () => clearInterval(t);
  }, [focused]);

  useEffect(() => {
    opacity.setValue(0);
    Animated.timing(opacity, { toValue: target, duration: 1400, useNativeDriver: true }).start();
  }, [index, target, opacity]);

  return (
    <View pointerEvents="none" style={[StyleSheet.absoluteFill, { backgroundColor: base }]}>
      <Animated.Image source={IMAGES[index]} resizeMode="cover" style={[StyleSheet.absoluteFill, { opacity }]} />
    </View>
  );
}
