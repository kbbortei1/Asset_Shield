import { Ionicons } from '@expo/vector-icons';
import { forwardRef, useState } from 'react';
import { Pressable, TextInput, TextInputProps, View } from 'react-native';
import { colors, radius, spacing, type } from '@/theme';
import { Text } from './Text';

export type InputProps = TextInputProps & {
  label?: string;
  error?: string | null;
  hint?: string;
};

/**
 * 56px, 12px-rounded field with a persistent label above (accessibility: never
 * placeholder-only). Hairline border, teal focus ring, red error state.
 * Password fields (secureTextEntry) get a built-in show/hide eye toggle.
 * Forwards its ref to the TextInput so forms can chain focus with
 * returnKeyType="next" + onSubmitEditing.
 */
export const Input = forwardRef<TextInput, InputProps>(function Input(
  { label, error, hint, style, onFocus, onBlur, secureTextEntry, ...rest },
  ref,
) {
  const [focused, setFocused] = useState(false);
  const [hidden, setHidden] = useState(true);
  const borderColor = error ? colors.error : focused ? colors.primary : colors.border;
  const isSecure = !!secureTextEntry;

  return (
    <View style={{ gap: spacing.xs, alignSelf: 'stretch' }}>
      {label ? (
        <Text variant="labelMd" color={colors.textMuted}>
          {label}
        </Text>
      ) : null}
      <View>
        <TextInput
          ref={ref}
          placeholderTextColor={colors.textMuted}
          secureTextEntry={isSecure && hidden}
          style={[
            {
              height: 56,
              borderRadius: radius.md,
              borderWidth: focused || error ? 1.5 : 1,
              borderColor,
              paddingHorizontal: spacing.lg,
              paddingRight: isSecure ? 52 : spacing.lg,
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
        {isSecure ? (
          <Pressable
            accessibilityRole="button"
            accessibilityLabel={hidden ? 'Show password' : 'Hide password'}
            hitSlop={10}
            onPress={() => setHidden((h) => !h)}
            style={{ position: 'absolute', right: spacing.lg, top: 0, height: 56, justifyContent: 'center' }}
          >
            <Ionicons name={hidden ? 'eye-outline' : 'eye-off-outline'} size={22} color={colors.textMuted} />
          </Pressable>
        ) : null}
      </View>
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
});
