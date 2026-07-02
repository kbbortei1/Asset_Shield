import { Text as RNText, TextProps as RNTextProps, TextStyle } from 'react-native';
import { colors, type, TypeName } from '@/theme';

export type TextProps = RNTextProps & {
  variant?: TypeName;
  color?: string;
  align?: TextStyle['textAlign'];
  weight?: 'regular' | 'medium' | 'semibold' | 'bold';
};

/**
 * Typed text primitive. `variant` maps to a Stitch typography role; `color`
 * defaults to ink. Use this everywhere instead of raw <Text> so the type scale
 * stays consistent.
 */
export function Text({ variant = 'bodyMd', color = colors.text, align, weight, style, ...rest }: TextProps) {
  const role = type[variant];
  return (
    <RNText
      style={[role, { color }, align ? { textAlign: align } : null, weightStyle(weight), style]}
      {...rest}
    />
  );
}

function weightStyle(weight?: TextProps['weight']): TextStyle | null {
  switch (weight) {
    case 'regular':
      return { fontFamily: 'Inter_400Regular' };
    case 'medium':
      return { fontFamily: 'Inter_500Medium' };
    case 'semibold':
      return { fontFamily: 'Inter_600SemiBold' };
    case 'bold':
      return { fontFamily: 'Inter_700Bold' };
    default:
      return null;
  }
}
