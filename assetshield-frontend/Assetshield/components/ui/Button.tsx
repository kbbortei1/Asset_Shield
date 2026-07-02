import { ActivityIndicator, Pressable, PressableProps, View, ViewStyle } from 'react-native';
import { colors, radius, spacing } from '@/theme';
import { Text } from './Text';

type Variant = 'primary' | 'secondary' | 'ghost' | 'danger';

export type ButtonProps = Omit<PressableProps, 'children'> & {
  title: string;
  variant?: Variant;
  loading?: boolean;
  fullWidth?: boolean;
  leftIcon?: React.ReactNode;
};

/**
 * Primary buttons: 56px tall, full-width, Trust Gold bg + Ink bold text (design.md).
 * Secondary = teal outline; ghost = text only; danger = alert red.
 */
export function Button({
  title,
  variant = 'primary',
  loading = false,
  fullWidth = true,
  disabled,
  leftIcon,
  style,
  ...rest
}: ButtonProps) {
  const isDisabled = disabled || loading;
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ disabled: !!isDisabled, busy: loading }}
      disabled={isDisabled}
      style={(state) => [
        base,
        fullWidth && { alignSelf: 'stretch' },
        variantStyle(variant, state.pressed),
        state.pressed && !isDisabled && { transform: [{ scale: 0.98 }] },
        isDisabled && { opacity: 0.5 },
        typeof style === 'function' ? style(state) : style,
      ]}
      {...rest}
    >
      {loading ? (
        <ActivityIndicator color={variant === 'primary' ? colors.onCta : colors.onPrimary} />
      ) : (
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: spacing.sm }}>
          {leftIcon}
          <Text variant="bodyMd" weight="bold" color={labelColor(variant)}>
            {title}
          </Text>
        </View>
      )}
    </Pressable>
  );
}

const base: ViewStyle = {
  height: 56,
  borderRadius: radius.md,
  alignItems: 'center',
  justifyContent: 'center',
  paddingHorizontal: spacing.lg,
};

function variantStyle(variant: Variant, pressed: boolean): ViewStyle {
  switch (variant) {
    case 'primary':
      return {
        backgroundColor: pressed ? colors.goldPressed : colors.cta,
        shadowColor: colors.cta,
        shadowOpacity: pressed ? 0.2 : 0.35,
        shadowRadius: 10,
        shadowOffset: { width: 0, height: 4 },
        elevation: pressed ? 2 : 5,
      };
    case 'secondary':
      return {
        backgroundColor: pressed ? colors.tealTint : 'transparent',
        borderWidth: 1.5,
        borderColor: colors.primary,
      };
    case 'danger':
      return { backgroundColor: colors.error, opacity: pressed ? 0.85 : 1 };
    case 'ghost':
      return { backgroundColor: pressed ? colors.tealTint : 'transparent' };
  }
}

function labelColor(variant: Variant): string {
  switch (variant) {
    case 'primary':
      return colors.onCta;
    case 'secondary':
    case 'ghost':
      return colors.primary;
    case 'danger':
      return colors.white;
  }
}
