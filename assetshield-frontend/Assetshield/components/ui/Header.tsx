import { Ionicons } from '@expo/vector-icons';
import { router } from 'expo-router';
import { Pressable, View } from 'react-native';
import { colors, spacing } from '@/theme';
import { Text } from './Text';

export type HeaderProps = {
  title?: string;
  showBack?: boolean;
  onBack?: () => void;
  right?: React.ReactNode;
};

/** Lightweight in-content header (use when not relying on the Stack header). */
export function Header({ title, showBack, onBack, right }: HeaderProps) {
  // Only offer "back" when there's actually somewhere to go (or a custom
  // handler). A screen reached via router.replace() has no history, so an
  // unguarded router.back() dispatches an unhandled GO_BACK. Callers can still
  // force the arrow on/off with an explicit showBack.
  const visible = showBack ?? (onBack != null || router.canGoBack());
  return (
    <View
      style={{
        flexDirection: 'row',
        alignItems: 'center',
        gap: spacing.md,
        paddingHorizontal: spacing.screenPadding,
        paddingVertical: spacing.md,
        // Transparent so the screen's themed backdrop shows through uniformly —
        // a solid fill here reads as a mismatched bar over the faint photo.
        backgroundColor: 'transparent',
      }}
    >
      {visible ? (
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="Go back"
          hitSlop={12}
          onPress={() => {
            if (onBack) return onBack();
            if (router.canGoBack()) router.back();
          }}
        >
          <Ionicons name="chevron-back" size={26} color={colors.text} />
        </Pressable>
      ) : null}
      {title ? (
        <Text variant="headlineSm" style={{ flex: 1 }} numberOfLines={1}>
          {title}
        </Text>
      ) : (
        <View style={{ flex: 1 }} />
      )}
      {right}
    </View>
  );
}
