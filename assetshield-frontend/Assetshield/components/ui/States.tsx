import { Ionicons } from '@expo/vector-icons';
import { ActivityIndicator, View } from 'react-native';
import { colors, spacing } from '@/theme';
import { Button } from './Button';
import { Text } from './Text';

/** Centered spinner for the loading state. */
export function Loading({ label }: { label?: string }) {
  return (
    <View style={{ flex: 1, alignItems: 'center', justifyContent: 'center', padding: spacing.xl, gap: spacing.md }}>
      <ActivityIndicator color={colors.primary} size="large" />
      {label ? (
        <Text variant="bodyMd" color={colors.textMuted}>
          {label}
        </Text>
      ) : null}
    </View>
  );
}

/** Empty state with an icon, title, optional body and CTA. */
export function EmptyState({
  icon = 'file-tray-outline',
  title,
  body,
  actionLabel,
  onAction,
}: {
  icon?: keyof typeof Ionicons.glyphMap;
  title: string;
  body?: string;
  actionLabel?: string;
  onAction?: () => void;
}) {
  return (
    <View style={{ alignItems: 'center', justifyContent: 'center', padding: spacing.xl, gap: spacing.md }}>
      <View
        style={{
          width: 88,
          height: 88,
          borderRadius: 44,
          backgroundColor: colors.tealTint,
          alignItems: 'center',
          justifyContent: 'center',
          marginBottom: spacing.xs,
        }}
      >
        <Ionicons name={icon} size={40} color={colors.primary} />
      </View>
      <Text variant="headlineSm" align="center">
        {title}
      </Text>
      {body ? (
        <Text variant="bodyMd" color={colors.textMuted} align="center">
          {body}
        </Text>
      ) : null}
      {actionLabel && onAction ? (
        <View style={{ marginTop: spacing.sm, alignSelf: 'stretch' }}>
          <Button title={actionLabel} onPress={onAction} />
        </View>
      ) : null}
    </View>
  );
}

/** Error state with retry. */
export function ErrorState({ message, onRetry }: { message?: string; onRetry?: () => void }) {
  return (
    <View style={{ alignItems: 'center', justifyContent: 'center', padding: spacing.xl, gap: spacing.md }}>
      <Ionicons name="alert-circle-outline" size={48} color={colors.error} />
      <Text variant="headlineSm" align="center">
        Something went wrong
      </Text>
      <Text variant="bodyMd" color={colors.textMuted} align="center">
        {message ?? 'Please try again.'}
      </Text>
      {onRetry ? (
        <View style={{ marginTop: spacing.sm, alignSelf: 'stretch' }}>
          <Button title="Try again" variant="secondary" onPress={onRetry} />
        </View>
      ) : null}
    </View>
  );
}
