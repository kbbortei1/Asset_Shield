import { ReactNode } from 'react';
import { ScrollView, StyleProp, View, ViewStyle, RefreshControl } from 'react-native';
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

/** Standard screen frame: warm background, safe-area, 24px horizontal padding. */
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
        <ScrollView
          style={{ flex: 1 }}
          contentContainerStyle={[{ paddingVertical: spacing.lg, gap: spacing.lg }, pad, contentStyle]}
          keyboardShouldPersistTaps="handled"
          showsVerticalScrollIndicator={false}
          refreshControl={
            onRefresh ? <RefreshControl refreshing={!!refreshing} onRefresh={onRefresh} tintColor={colors.primary} /> : undefined
          }
        >
          {children}
        </ScrollView>
      ) : (
        <View style={[{ flex: 1, paddingVertical: spacing.lg, gap: spacing.lg }, pad, contentStyle]}>{children}</View>
      )}
        {footer ? <View style={[{ paddingVertical: spacing.md }, pad]}>{footer}</View> : null}
      </SafeAreaView>
    </View>
  );
}
