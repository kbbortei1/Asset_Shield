import { ReactNode, useState } from 'react';
import { LayoutChangeEvent, StyleProp, View, ViewStyle, RefreshControl } from 'react-native';
import { KeyboardAwareScrollView, KeyboardStickyView } from 'react-native-keyboard-controller';
import { SafeAreaView, Edge, useSafeAreaInsets } from 'react-native-safe-area-context';
import { colors, spacing } from '@/theme';
import { AppBackground } from './AppBackground';

export type ScreenProps = {
  children: ReactNode;
  scroll?: boolean;
  padded?: boolean;
  edges?: Edge[];
  contentStyle?: StyleProp<ViewStyle>;
  refreshing?: boolean;
  onRefresh?: () => void;
  footer?: ReactNode;
};

/**
 * Standard screen frame: themed evidence backdrop, safe-area, 24px padding.
 * Scrolling is keyboard-aware (react-native-keyboard-controller): focused
 * inputs scroll into view above the keyboard on BOTH platforms, dragging
 * dismisses the keyboard, and the footer sticks above the keyboard instead of
 * being covered (the iOS failure mode of plain ScrollViews).
 */
export function Screen({
  children,
  scroll = true,
  padded = true,
  edges = ['top', 'bottom'],
  contentStyle,
  refreshing,
  onRefresh,
  footer,
}: ScreenProps) {
  const pad: ViewStyle = padded ? { paddingHorizontal: spacing.screenPadding } : {};
  // Lift a focused input above BOTH the keyboard and the sticky footer, so the
  // Save button never overlaps the field being typed into. Measured, not
  // guessed, so it's correct for any footer height.
  const [footerHeight, setFooterHeight] = useState(0);
  const onFooterLayout = (e: LayoutChangeEvent) => setFooterHeight(e.nativeEvent.layout.height);
  // The footer sits inside the bottom safe-area, but KeyboardStickyView lifts it
  // by the full keyboard height — so on 3-button-nav phones (big bottom inset)
  // it overshoots the keyboard top and floats over the form. Pull it back down
  // by the inset so it lands exactly on the keyboard. (~0 on gesture nav.)
  const insets = useSafeAreaInsets();

  return (
    <View style={{ flex: 1 }}>
      {/* Opaque themed base + faint rotating photo. Being opaque, it stops the
          previous screen showing through during navigation transitions. */}
      <AppBackground />
      <SafeAreaView style={{ flex: 1, backgroundColor: 'transparent' }} edges={edges}>
        {scroll ? (
          <KeyboardAwareScrollView
            style={{ flex: 1 }}
            bottomOffset={(footer ? footerHeight : 0) + 24}
            contentContainerStyle={[{ paddingVertical: spacing.lg, gap: spacing.lg }, pad, contentStyle]}
            keyboardShouldPersistTaps="handled"
            keyboardDismissMode="on-drag"
            showsVerticalScrollIndicator={false}
            refreshControl={
              onRefresh ? <RefreshControl refreshing={!!refreshing} onRefresh={onRefresh} tintColor={colors.primary} /> : undefined
            }
          >
            {children}
          </KeyboardAwareScrollView>
        ) : (
          <View style={[{ flex: 1, paddingVertical: spacing.lg, gap: spacing.lg }, pad, contentStyle]}>{children}</View>
        )}
        {footer ? (
          <KeyboardStickyView offset={{ opened: insets.bottom }}>
            {/* Opaque bar with a top divider: when the keyboard lifts this above
                the content, the field beneath it must NOT bleed through (that was
                the "Save button overlapping the input" bug). */}
            <View
              onLayout={onFooterLayout}
              style={[{ paddingVertical: spacing.md, backgroundColor: colors.background, borderTopWidth: 1, borderTopColor: colors.border }, pad]}
            >
              {footer}
            </View>
          </KeyboardStickyView>
        ) : null}
      </SafeAreaView>
    </View>
  );
}
