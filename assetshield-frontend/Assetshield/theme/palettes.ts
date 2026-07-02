/**
 * Theme palettes. All three share the exact same keys so `colors` can be swapped
 * wholesale at runtime (see colors.ts + ThemeProvider). Semantic tokens
 * (background/text/primary/…) carry the theme; brand hues stay recognizable.
 */

export const lightColors = {
  // brand
  teal: '#0E5A52',
  tealDeep: '#00413B',
  tealTint: 'rgba(14, 90, 82, 0.10)',
  tealMuted: '#A6CFC8',
  gold: '#F4A93C',
  goldPressed: '#E0982C',
  // neutrals
  surface: '#F6F5F1',
  card: '#FFFFFF',
  ink: '#11252B',
  slate: '#5C6B70',
  hairline: '#E3E6E4',
  white: '#FFFFFF',
  black: '#000000',
  // functional
  secured: '#1B7F58',
  damaged: '#BA1A1A',
  warning: '#845400',
  // semantic
  primary: '#0E5A52',
  primaryDeep: '#00413B',
  onPrimary: '#FFFFFF',
  cta: '#F4A93C',
  onCta: '#11252B',
  background: '#F6F5F1',
  text: '#11252B',
  textMuted: '#5C6B70',
  border: '#E3E6E4',
  success: '#1B7F58',
  error: '#BA1A1A',
  statusSecuredBg: '#1B7F58',
  statusNeedsUpdateBg: '#5C6B70',
  statusDamagedBg: '#BA1A1A',
  shadow: 'rgba(17, 37, 43, 0.06)',
};

export type Palette = typeof lightColors;

/** Dark — charcoal surfaces, teal-forward accents. */
export const darkColors: Palette = {
  teal: '#0E5A52',
  tealDeep: '#00413B',
  tealTint: 'rgba(145, 211, 200, 0.14)',
  tealMuted: '#A6CFC8',
  gold: '#F4A93C',
  goldPressed: '#E0982C',
  surface: '#0F1613',
  card: '#1A2320',
  ink: '#ECEFEC',
  slate: '#9BA8A4',
  hairline: '#2C3833',
  white: '#FFFFFF',
  black: '#000000',
  secured: '#35C08A',
  damaged: '#FF6B6B',
  warning: '#E0982C',
  primary: '#3FA392',
  primaryDeep: '#0E5A52',
  onPrimary: '#04231F',
  cta: '#F4A93C',
  onCta: '#11252B',
  background: '#0F1613',
  text: '#ECEFEC',
  textMuted: '#9BA8A4',
  border: '#2C3833',
  success: '#35C08A',
  error: '#FF6B6B',
  statusSecuredBg: '#1B7F58',
  statusNeedsUpdateBg: '#3C4A45',
  statusDamagedBg: '#B3261E',
  shadow: 'rgba(0, 0, 0, 0.5)',
};

/** Gold — luxe dark with gold as the primary accent. */
export const goldColors: Palette = {
  teal: '#0E5A52',
  tealDeep: '#00413B',
  tealTint: 'rgba(244, 169, 60, 0.14)',
  tealMuted: '#E9CE93',
  gold: '#F4A93C',
  goldPressed: '#E0982C',
  surface: '#141210',
  card: '#201C15',
  ink: '#F3ECDD',
  slate: '#B8AC90',
  hairline: '#332C1F',
  white: '#FFFFFF',
  black: '#000000',
  secured: '#35C08A',
  damaged: '#FF6B6B',
  warning: '#E0982C',
  primary: '#E0982C',
  primaryDeep: '#A66A12',
  onPrimary: '#231A08',
  cta: '#F4A93C',
  onCta: '#231A08',
  background: '#141210',
  text: '#F3ECDD',
  textMuted: '#B8AC90',
  border: '#332C1F',
  success: '#35C08A',
  error: '#FF6B6B',
  statusSecuredBg: '#1B7F58',
  statusNeedsUpdateBg: '#4A4230',
  statusDamagedBg: '#B3261E',
  shadow: 'rgba(0, 0, 0, 0.5)',
};

export type ThemeName = 'light' | 'dark' | 'gold';

export const palettes: Record<ThemeName, Palette> = {
  light: lightColors,
  dark: darkColors,
  gold: goldColors,
};
