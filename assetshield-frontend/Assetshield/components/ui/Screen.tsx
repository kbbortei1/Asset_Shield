import { ReactNode } from 'react';
import { StyleProp, View, ViewStyle, RefreshControl } from 'react-native';
import { KeyboardAwareScrollView, KeyboardStickyView } from 'react-native-keyboard-controller';
import { SafeAreaView, Edge } from 'react-native-safe-area-context';
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

  return (
    <View style={{ flex: 1 }}>
      {/* Opaque themed base + faint rotating photo. Being opaque, it stops the
          previous screen showing through during navigation transitions. */}
      <AppBackground />
      <SafeAreaView style={{ flex: 1, backgroundColor: 'transparent' }} edges={edges}>
        {scroll ? (
          <KeyboardAwareScrollView
            style={{ flex: 1 }}
            bottomOffset={24}
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
          <KeyboardStickyView>
            <View style={[{ paddingVertical: spacing.md, backgroundColor: 'transparent' }, pad]}>{footer}</View>
          </KeyboardStickyView>
        ) : null}
      </SafeAreaView>
    </View>
  );
}
