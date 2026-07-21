import { Ionicons } from '@expo/vector-icons';
import { createContext, useCallback, useContext, useMemo, useRef, useState } from 'react';
import { Modal, Pressable, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { colors, radius, spacing } from '@/theme';
import { Button } from './Button';
import { Input } from './Input';
import { Text } from './Text';

export type ConfirmOptions = {
  title: string;
  message?: string;
  confirmLabel?: string;
  cancelLabel?: string;
  /** Red confirm button + warning icon tint. */
  destructive?: boolean;
  icon?: keyof typeof Ionicons.glyphMap;
  /**
   * Ask for the account password before confirming. `resolve` only returns the
   * typed password (via onConfirmWithPassword) when this is set; the caller
   * verifies it and is responsible for surfacing a wrong-password error.
   */
  requirePassword?: boolean;
  passwordError?: string;
};

type ConfirmResult = { confirmed: boolean; password?: string };
type ConfirmFn = (opts: ConfirmOptions) => Promise<ConfirmResult>;

const ConfirmContext = createContext<ConfirmFn | null>(null);

/** Imperative, promise-based confirmation: `if ((await confirm({...})).confirmed) …`. */
export function useConfirm(): ConfirmFn {
  const ctx = useContext(ConfirmContext);
  if (!ctx) throw new Error('useConfirm must be used within ConfirmProvider');
  return ctx;
}

export function ConfirmProvider({ children }: { children: React.ReactNode }) {
  const insets = useSafeAreaInsets();
  const [opts, setOpts] = useState<ConfirmOptions | null>(null);
  const [password, setPassword] = useState('');
  const resolver = useRef<((r: ConfirmResult) => void) | null>(null);

  const confirm = useCallback<ConfirmFn>((next) => {
    setPassword('');
    setOpts(next);
    return new Promise<ConfirmResult>((resolve) => {
      resolver.current = resolve;
    });
  }, []);

  const settle = useCallback((result: ConfirmResult) => {
    resolver.current?.(result);
    resolver.current = null;
    setOpts(null);
    setPassword('');
  }, []);

  const value = useMemo(() => confirm, [confirm]);
  const destructive = opts?.destructive;
  const icon = opts?.icon ?? (destructive ? 'warning' : 'help-circle');
  const canConfirm = !opts?.requirePassword || password.trim().length > 0;

  return (
    <ConfirmContext.Provider value={value}>
      {children}
      <Modal
        visible={opts !== null}
        transparent
        animationType="slide"
        onRequestClose={() => settle({ confirmed: false })}
      >
        <Pressable
          style={{ flex: 1, backgroundColor: 'rgba(17,37,43,0.5)' }}
          onPress={() => settle({ confirmed: false })}
        >
          {/* stop propagation so taps inside the sheet don't dismiss it */}
          <Pressable
            onPress={() => {}}
            style={{
              marginTop: 'auto',
              backgroundColor: colors.background,
              borderTopLeftRadius: radius.xl,
              borderTopRightRadius: radius.xl,
              padding: spacing.xl,
              paddingBottom: insets.bottom + spacing.xl,
              gap: spacing.md,
            }}
          >
            <View style={{ alignItems: 'center', marginBottom: spacing.xs }}>
              <View style={{ width: 40, height: 4, borderRadius: 2, backgroundColor: colors.border }} />
            </View>

            <View
              style={{
                alignSelf: 'center',
                width: 56,
                height: 56,
                borderRadius: 28,
                backgroundColor: destructive ? 'rgba(186,26,26,0.12)' : colors.tealTint,
                alignItems: 'center',
                justifyContent: 'center',
              }}
            >
              <Ionicons name={icon} size={28} color={destructive ? colors.error : colors.primary} />
            </View>

            <Text variant="headlineSm" style={{ textAlign: 'center' }}>
              {opts?.title}
            </Text>
            {opts?.message ? (
              <Text variant="bodyMd" color={colors.textMuted} style={{ textAlign: 'center' }}>
                {opts.message}
              </Text>
            ) : null}

            {opts?.requirePassword ? (
              <View style={{ gap: spacing.xs, marginTop: spacing.xs }}>
                <Input
                  label="Enter your password to confirm"
                  value={password}
                  onChangeText={setPassword}
                  secureTextEntry
                  autoCapitalize="none"
                  placeholder="Password"
                />
                {opts.passwordError ? (
                  <Text variant="labelMd" color={colors.error}>
                    {opts.passwordError}
                  </Text>
                ) : null}
              </View>
            ) : null}

            <View style={{ gap: spacing.sm, marginTop: spacing.sm }}>
              <Button
                title={opts?.confirmLabel ?? 'Confirm'}
                variant={destructive ? 'danger' : 'primary'}
                disabled={!canConfirm}
                onPress={() =>
                  settle({ confirmed: true, password: opts?.requirePassword ? password : undefined })
                }
              />
              <Button
                title={opts?.cancelLabel ?? 'Cancel'}
                variant="secondary"
                onPress={() => settle({ confirmed: false })}
              />
            </View>
          </Pressable>
        </Pressable>
      </Modal>
    </ConfirmContext.Provider>
  );
}
