import { useState } from 'react';
import { TextInput, View } from 'react-native';
import { colors, radius, spacing, type } from '@/theme';
import { Text } from './Text';

export type PhoneInputProps = {
  label?: string;
  /** Full E.164 value, e.g. "+233201112233". */
  value: string;
  /** Called with the full "+233XXXXXXXXX" value. */
  onChangeText: (full: string) => void;
  error?: string | null;
  hint?: string;
  autoFocus?: boolean;
};

export const GH_PREFIX = '+233';

/** Extract the 9 local digits from a full/partial +233 number. */
function localPart(value: string): string {
  let v = value.replace(/[^\d+]/g, '');
  if (v.startsWith(GH_PREFIX)) v = v.slice(GH_PREFIX.length);
  else if (v.startsWith('+')) v = v.slice(1);
  if (v.startsWith('233')) v = v.slice(3);
  if (v.startsWith('0')) v = v.slice(1); // people habitually type the local 0 prefix
  return v.replace(/\D/g, '').slice(0, 9);
}

/** True when the value is a complete, valid Ghana number (+233 + 9 digits). */
export function isCompleteGhPhone(value: string): boolean {
  return /^\+233\d{9}$/.test(value);
}

/**
 * Ghana phone field with a read-only +233 prefix. The backend only accepts
 * +233XXXXXXXXX, so the code is locked rather than user-editable; a typed
 * leading 0 (local habit, e.g. 020 111 2233) is stripped automatically.
 */
export function PhoneInput({ label = 'Phone number', value, onChangeText, error, hint, autoFocus }: PhoneInputProps) {
  const [focused, setFocused] = useState(false);
  const borderColor = error ? colors.error : focused ? colors.primary : colors.border;
  const digits = localPart(value);

  return (
    <View style={{ gap: spacing.xs, alignSelf: 'stretch' }}>
      {label ? (
        <Text variant="labelMd" color={colors.textMuted}>
          {label}
        </Text>
      ) : null}
      <View
        style={{
          flexDirection: 'row',
          alignItems: 'center',
          height: 56,
          borderRadius: radius.md,
          borderWidth: focused || error ? 1.5 : 1,
          borderColor,
          backgroundColor: colors.card,
          overflow: 'hidden',
        }}
      >
        <View
          style={{
            height: '100%',
            justifyContent: 'center',
            paddingHorizontal: spacing.md,
            backgroundColor: colors.tealTint,
            borderRightWidth: 1,
            borderRightColor: colors.border,
          }}
        >
          <Text variant="bodyMd" weight="semibold" color={colors.primary}>
            {GH_PREFIX}
          </Text>
        </View>
        <TextInput
          value={digits}
          onChangeText={(t) => onChangeText(GH_PREFIX + localPart(t))}
          keyboardType="number-pad"
          maxLength={10} // 9 digits + headroom for a pasted leading 0
          autoFocus={autoFocus}
          placeholder="201112233"
          placeholderTextColor={colors.textMuted}
          accessibilityLabel="Ghana phone number without the country code"
          style={{ flex: 1, height: '100%', paddingHorizontal: spacing.lg, color: colors.text, ...type.bodyMd }}
          onFocus={() => setFocused(true)}
          onBlur={() => setFocused(false)}
        />
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
}
