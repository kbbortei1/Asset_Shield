export { colors, applyThemeColors } from './colors';
export type { ColorToken } from './colors';
export { palettes } from './palettes';
export type { ThemeName, Palette } from './palettes';
export { type, fontFamily } from './typography';
export type { TypeName } from './typography';
export { spacing, radius, elevation } from './spacing';

import { colors } from './colors';
import { spacing, radius, elevation } from './spacing';
import { type } from './typography';

export const theme = { colors, spacing, radius, elevation, type } as const;
export type Theme = typeof theme;
