import { Ionicons } from '@expo/vector-icons';
import { View } from 'react-native';
import { colors, radius, spacing } from '@/theme';
import { Button } from './Button';
import { Text } from './Text';

export type GatedNoticeProps = {
  icon?: keyof typeof Ionicons.glyphMap;
  title: string;
  body: string;
  actionLabel?: string;
  onAction?: () => void;
  /** 'lock' = subscription/verification wall (gold), 'info' = neutral (teal). */
  tone?: 'lock' | 'info';
};

/**
 * A calm, branded "you can't do this yet" panel — used where a plain error
 * would be wrong (e.g. an unsubscribed agent opening Dossiers). Replaces the
 * generic "Something went wrong" for gated, expected states.
 */
export function GatedNotice({ icon, title, body, actionLabel, onAction, tone = 'lock' }: GatedNoticeProps) {
  const accent = tone === 'lock' ? colors.cta : colors.primary;
  const badgeBg = tone === 'lock' ? 'rgba(244,169,60,0.14)' : colors.tealTint;
  const glyph = icon ?? (tone === 'lock' ? 'lock-closed' : 'information-circle');
  return (
    <View
      style={{
        alignItems: 'center',
        gap: spacing.md,
        paddingVertical: spacing.xxl,
        paddingHorizontal: spacing.xl,
        borderRadius: radius.lg,
        borderWidth: 1,
        borderColor: colors.border,
        backgroundColor: colors.card,
      }}
    >
      <View
        style={{
          width: 64,
          height: 64,
          borderRadius: 32,
          backgroundColor: badgeBg,
          alignItems: 'center',
          justifyContent: 'center',
        }}
      >
        <Ionicons name={glyph} size={30} color={accent} />
      </View>
      <Text variant="headlineSm" style={{ textAlign: 'center' }}>
        {title}
      </Text>
      <Text variant="bodyMd" color={colors.textMuted} style={{ textAlign: 'center' }}>
        {body}
      </Text>
      {actionLabel && onAction ? (
        <View style={{ alignSelf: 'stretch', marginTop: spacing.sm }}>
          <Button title={actionLabel} onPress={onAction} />
        </View>
      ) : null}
    </View>
  );
}
