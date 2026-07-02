import { useState } from 'react';
import { TextInput, TextInputProps, View } from 'react-native';
import { colors, radius, spacing, type } from '@/theme';
import { Text } from './Text';

export type InputProps = TextInputProps & {
  label?: string;
  error?: string | null;
  hint?: string;
};

/**
 * 56px, 12px-rounded field with a persistent label above (accessibility — never
 * placeholder-only). Hairline border, teal focus ring, red error state.
 */
export function Input({ label, error, hint, style, onFocus, onBlur, ...rest }: InputProps) {
  const [focused, setFocused] = useState(false);
  const borderColor = error ? colors.error : focused ? colors.primary : colors.border;
  return (
    <View style={{ gap: spacing.xs, alignSelf: 'stretch' }}>
      {label ? (
        <Text variant="labelMd" color={colors.textMuted}>
          {label}
        </Text>
      ) : null}
      <TextInput
        placeholderTextColor={colors.textMuted}
        style={[
          {
            height: 56,
            borderRadius: radius.md,
            borderWidth: focused || error ? 1.5 : 1,
            borderColor,
            paddingHorizontal: spacing.lg,
            backgroundColor: colors.card,
            color: colors.text,
            ...type.bodyMd,
          },
          style,
        ]}
        onFocus={(e) => {
          setFocused(true);
          onFocus?.(e);
        }}
        onBlur={(e) => {
          setFocused(false);
          onBlur?.(e);
        }}
        {...rest}
      />
      {error ? (
        <Text variant="labelMd" color={colors.error}>
          {error}
        </Text>
      ) : hint ? (
        <Text variant="labelMd" color={colors.textMuted}>
          {hint}
        </Text>
      ) : null}
    </View>
  );
}
