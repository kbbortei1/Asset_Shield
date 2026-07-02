/**
 * Typography roles — from the Stitch design system.
 * Headings: Sora (geometric, confident). Body: Inter (legible on mobile).
 * Monetary values use `currencyDisplay`. Line heights are generous to leave
 * room for Twi translations.
 *
 * fontFamily values match the @expo-google-fonts export names loaded in the
 * root layout. If a font fails to load, RN falls back to the system face at the
 * same size/weight, so these are safe to reference before fonts resolve.
 */

export const fontFamily = {
  // Sora
  soraSemiBold: 'Sora_600SemiBold',
  soraBold: 'Sora_700Bold',
  // Inter
  interRegular: 'Inter_400Regular',
  interMedium: 'Inter_500Medium',
  interSemiBold: 'Inter_600SemiBold',
  interBold: 'Inter_700Bold',
} as const;

type TypeRole = {
  fontFamily: string;
  fontSize: number;
  lineHeight: number;
  letterSpacing?: number;
};

// Line heights are kept comfortably above the cap height so Sora/Inter never
// clip or overlap on Android (which packs custom-font lines tightly).
export const type: Record<string, TypeRole> = {
  headlineLg: { fontFamily: fontFamily.soraBold, fontSize: 30, lineHeight: 40, letterSpacing: -0.6 },
  headlineLgMobile: { fontFamily: fontFamily.soraBold, fontSize: 26, lineHeight: 34 },
  headlineMd: { fontFamily: fontFamily.soraSemiBold, fontSize: 24, lineHeight: 32 },
  headlineSm: { fontFamily: fontFamily.soraSemiBold, fontSize: 20, lineHeight: 28 },
  bodyLg: { fontFamily: fontFamily.interRegular, fontSize: 18, lineHeight: 28 },
  bodyMd: { fontFamily: fontFamily.interRegular, fontSize: 16, lineHeight: 24 },
  labelMd: { fontFamily: fontFamily.interMedium, fontSize: 14, lineHeight: 20, letterSpacing: 0.14 },
  currencyDisplay: { fontFamily: fontFamily.soraBold, fontSize: 32, lineHeight: 42 },
} as const;

export type TypeName = keyof typeof type;
