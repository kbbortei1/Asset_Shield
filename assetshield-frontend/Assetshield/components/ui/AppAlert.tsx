import { Ionicons } from '@expo/vector-icons';
import { useEffect, useState } from 'react';
import { Modal, Pressable, View } from 'react-native';
import { colors, radius, spacing } from '@/theme';
import { Button } from './Button';
import { Text } from './Text';

/**
 * A designed, theme-aware replacement for React Native's `Alert.alert`. Same
 * call signature (title, message, buttons) so it's a drop-in — swap
 * `Alert.alert(...)` for `showAlert(...)` and the dialog now matches the app's
 * look instead of the raw OS popup. Backed by a single host mounted at the root.
 */
export type AppAlertButton = {
  text?: string;
  onPress?: () => void;
  style?: 'default' | 'cancel' | 'destructive';
};

type AlertConfig = { title: string; message?: string; buttons?: AppAlertButton[] };

let hostShow: ((cfg: AlertConfig) => void) | null = null;

export function showAlert(title: string, message?: string, buttons?: AppAlertButton[]) {
  if (hostShow) hostShow({ title, message, buttons });
  // If the host isn't mounted yet (shouldn't happen in-app), fail silently
  // rather than crash — the action the alert accompanies still ran.
}

const ERROR_RE = /fail|error|could ?n'?t|cannot|can'?t|invalid|denied|wrong|declined|not completed|unable|problem/i;
const SUCCESS_RE = /success|confirmed|saved|sent|updated|complete|added|done/i;

/** Mount once near the app root (inside the providers). */
export function AppAlertHost() {
  const [cfg, setCfg] = useState<AlertConfig | null>(null);

  useEffect(() => {
    hostShow = (c) => setCfg(c);
    return () => {
      hostShow = null;
    };
  }, []);

  const close = () => setCfg(null);
  const buttons = cfg?.buttons?.length ? cfg.buttons : [{ text: 'OK' }];
  const tone = cfg
    ? ERROR_RE.test(cfg.title)
      ? 'error'
      : SUCCESS_RE.test(cfg.title)
        ? 'success'
        : 'info'
    : 'info';
  const accent = tone === 'error' ? colors.error : tone === 'success' ? colors.success : colors.primary;
  const icon = tone === 'error' ? 'alert-circle' : tone === 'success' ? 'checkmark-circle' : 'information-circle';
  const tint = tone === 'error' ? 'rgba(186,26,26,0.12)' : tone === 'success' ? 'rgba(31,138,112,0.12)' : colors.tealTint;

  return (
    <Modal visible={cfg !== null} transparent animationType="fade" onRequestClose={close}>
      <Pressable style={{ flex: 1, backgroundColor: 'rgba(17,37,43,0.5)', alignItems: 'center', justifyContent: 'center', padding: spacing.xl }} onPress={close}>
        <Pressable
          onPress={() => {}}
          style={{
            width: '100%',
            maxWidth: 380,
            backgroundColor: colors.background,
            borderRadius: radius.xl,
            padding: spacing.xl,
            gap: spacing.md,
            alignItems: 'stretch',
          }}
        >
          <View style={{ alignSelf: 'center', width: 56, height: 56, borderRadius: 28, backgroundColor: tint, alignItems: 'center', justifyContent: 'center' }}>
            <Ionicons name={icon} size={30} color={accent} />
          </View>

          <Text variant="headlineSm" style={{ textAlign: 'center' }}>
            {cfg?.title}
          </Text>
          {cfg?.message ? (
            <Text variant="bodyMd" color={colors.textMuted} style={{ textAlign: 'center' }}>
              {cfg.message}
            </Text>
          ) : null}

          <View style={{ gap: spacing.sm, marginTop: spacing.sm }}>
            {buttons.map((b, i) => (
              <Button
                key={i}
                title={b.text ?? 'OK'}
                variant={b.style === 'destructive' ? 'danger' : b.style === 'cancel' ? 'secondary' : i === buttons.length - 1 ? 'primary' : 'secondary'}
                onPress={() => {
                  close();
                  b.onPress?.();
                }}
              />
            ))}
          </View>
        </Pressable>
      </Pressable>
    </Modal>
  );
}
