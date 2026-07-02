/**
 * AssetShield GH colors. `colors` is a single mutable object that the
 * ThemeProvider swaps between light/dark/gold palettes at runtime via
 * applyThemeColors(). Components read `colors.x` at render time, so a swap +
 * remount recolors the whole app without per-screen theme wiring.
 */
import { lightColors, Palette, palettes, ThemeName } from './palettes';

export const colors: Palette = { ...lightColors };

/** Replace the live palette in place (same object reference). */
export function applyThemeColors(name: ThemeName): void {
  Object.assign(colors, palettes[name]);
}

export type ColorToken = keyof Palette;
