import { Ionicons } from '@expo/vector-icons';
import { createContext, useCallback, useContext, useMemo, useRef, useState } from 'react';
import { Modal, Pressable, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { colors, radius, spacing } from '@/theme';
import { Text } from './Text';

export type ActionOption = {
  label: string;
  value: string;
  icon?: keyof typeof Ionicons.glyphMap;
  destructive?: boolean;
};

export type ActionSheetOptions = {
  title?: string;
  message?: string;
  options: ActionOption[];
};

type ShowFn = (opts: ActionSheetOptions) => Promise<string | null>;

const ActionSheetContext = createContext<ShowFn | null>(null);

/** Imperative, promise-based chooser: `const v = await showActions({...})` (null = cancelled). */
export function useActionSheet(): ShowFn {
  const ctx = useContext(ActionSheetContext);
  if (!ctx) throw new Error('useActionSheet must be used within ActionSheetProvider');
  return ctx;
}

export function ActionSheetProvider({ children }: { children: React.ReactNode }) {
  const insets = useSafeAreaInsets();
  const [opts, setOpts] = useState<ActionSheetOptions | null>(null);
  const resolver = useRef<((v: string | null) => void) | null>(null);

  const show = useCallback<ShowFn>((next) => {
    setOpts(next);
    return new Promise<string | null>((resolve) => {
      resolver.current = resolve;
    });
  }, []);

  const settle = useCallback((value: string | null) => {
    resolver.current?.(value);
    resolver.current = null;
    setOpts(null);
  }, []);

  const value = useMemo(() => show, [show]);

  return (
    <ActionSheetContext.Provider value={value}>
      {children}
      <Modal
        visible={opts !== null}
        transparent
        animationType="slide"
        onRequestClose={() => settle(null)}
      >
        <Pressable style={{ flex: 1, backgroundColor: 'rgba(17,37,43,0.5)' }} onPress={() => settle(null)}>
          <Pressable
            onPress={() => {}}
            style={{
              marginTop: 'auto',
              backgroundColor: colors.background,
              borderTopLeftRadius: radius.xl,
              borderTopRightRadius: radius.xl,
              padding: spacing.xl,
              paddingBottom: insets.bottom + spacing.lg,
              gap: spacing.sm,
            }}
          >
            <View style={{ alignItems: 'center', marginBottom: spacing.xs }}>
              <View style={{ width: 40, height: 4, borderRadius: 2, backgroundColor: colors.border }} />
            </View>

            {opts?.title ? (
              <Text variant="headlineSm" style={{ textAlign: 'center' }}>
                {opts.title}
              </Text>
            ) : null}
            {opts?.message ? (
              <Text variant="labelMd" color={colors.textMuted} style={{ textAlign: 'center', marginBottom: spacing.sm }}>
                {opts.message}
              </Text>
            ) : null}

            {(opts?.options ?? []).map((o) => (
              <Pressable
                key={o.value}
                onPress={() => settle(o.value)}
                style={({ pressed }) => ({
                  flexDirection: 'row',
                  alignItems: 'center',
                  gap: spacing.md,
                  paddingVertical: spacing.md,
                  paddingHorizontal: spacing.md,
                  borderRadius: radius.md,
                  backgroundColor: pressed ? colors.tealTint : colors.card,
                  borderWidth: 1,
                  borderColor: colors.border,
                })}
              >
                {o.icon ? (
                  <View
                    style={{
                      width: 40,
                      height: 40,
                      borderRadius: radius.md,
                      alignItems: 'center',
                      justifyContent: 'center',
                      backgroundColor: o.destructive ? 'rgba(186,26,26,0.12)' : colors.tealTint,
                    }}
                  >
                    <Ionicons name={o.icon} size={20} color={o.destructive ? colors.error : colors.primary} />
                  </View>
                ) : null}
                <Text variant="bodyMd" weight="semibold" color={o.destructive ? colors.error : colors.text} style={{ flex: 1 }}>
                  {o.label}
                </Text>
              </Pressable>
            ))}

            <Pressable
              onPress={() => settle(null)}
              style={({ pressed }) => ({
                paddingVertical: spacing.md,
                borderRadius: radius.md,
                alignItems: 'center',
                marginTop: spacing.xs,
                backgroundColor: pressed ? colors.tealTint : 'transparent',
              })}
            >
              <Text variant="bodyMd" weight="semibold" color={colors.textMuted}>
                Cancel
              </Text>
            </Pressable>
          </Pressable>
        </Pressable>
      </Modal>
    </ActionSheetContext.Provider>
  );
}
