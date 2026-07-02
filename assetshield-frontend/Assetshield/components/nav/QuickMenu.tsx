import { Ionicons } from '@expo/vector-icons';
import { router } from 'expo-router';
import { useState } from 'react';
import { Modal, Pressable, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Text } from '@/components/ui';
import { colors, radius, spacing } from '@/theme';

export type QuickAction = {
  icon: keyof typeof Ionicons.glyphMap;
  label: string;
  href: string;
};

/**
 * Floating "+" button centered above the tab bar. Tapping it opens a bottom
 * sheet of the destinations/actions that don't have a dedicated tab, so the bar
 * itself stays at four tabs.
 */
export function QuickMenu({ actions }: { actions: QuickAction[] }) {
  const insets = useSafeAreaInsets();
  const [open, setOpen] = useState(false);
  if (actions.length === 0) return null;

  const go = (href: string) => {
    setOpen(false);
    requestAnimationFrame(() => router.push(href as never));
  };

  return (
    <>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel="More actions"
        onPress={() => setOpen(true)}
        style={{
          position: 'absolute',
          right: spacing.xl,
          bottom: insets.bottom + 74, // clears the 64px tab bar so it never covers a tab
          width: 56,
          height: 56,
          borderRadius: 28,
          backgroundColor: colors.cta,
          alignItems: 'center',
          justifyContent: 'center',
          shadowColor: colors.black,
          shadowOpacity: 0.2,
          shadowRadius: 8,
          shadowOffset: { width: 0, height: 4 },
          elevation: 8,
        }}
      >
        <Ionicons name="add" size={32} color={colors.onCta} />
      </Pressable>

      <Modal visible={open} transparent animationType="slide" onRequestClose={() => setOpen(false)}>
        <Pressable style={{ flex: 1, backgroundColor: 'rgba(17,37,43,0.45)' }} onPress={() => setOpen(false)}>
          <View
            style={{
              marginTop: 'auto',
              backgroundColor: colors.background,
              borderTopLeftRadius: radius.xl,
              borderTopRightRadius: radius.xl,
              padding: spacing.xl,
              paddingBottom: insets.bottom + spacing.xl,
              gap: spacing.sm,
            }}
          >
            <View style={{ alignItems: 'center', marginBottom: spacing.sm }}>
              <View style={{ width: 40, height: 4, borderRadius: 2, backgroundColor: colors.border }} />
            </View>
            <Text variant="headlineSm" style={{ marginBottom: spacing.sm }}>
              Quick menu
            </Text>
            {actions.map((a) => (
              <Pressable
                key={a.href + a.label}
                onPress={() => go(a.href)}
                style={({ pressed }) => ({
                  flexDirection: 'row',
                  alignItems: 'center',
                  gap: spacing.lg,
                  paddingVertical: spacing.md,
                  paddingHorizontal: spacing.md,
                  borderRadius: radius.md,
                  backgroundColor: pressed ? colors.tealTint : 'transparent',
                })}
              >
                <View
                  style={{
                    width: 44,
                    height: 44,
                    borderRadius: radius.md,
                    backgroundColor: colors.tealTint,
                    alignItems: 'center',
                    justifyContent: 'center',
                  }}
                >
                  <Ionicons name={a.icon} size={22} color={colors.primary} />
                </View>
                <Text variant="bodyMd" weight="semibold" style={{ flex: 1 }}>
                  {a.label}
                </Text>
                <Ionicons name="chevron-forward" size={18} color={colors.textMuted} />
              </Pressable>
            ))}
          </View>
        </Pressable>
      </Modal>
    </>
  );
}
