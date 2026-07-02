/**
 * 8pt spacing grid + shape language from the Stitch design system.
 * - Screen horizontal padding: 24px
 * - Vertical gap between major cards: 16px
 * - Min touch target: 48px
 * Shapes: cards 16px, interactive (button/input) 12px, pills fully rounded.
 */

export const spacing = {
  xs: 4,
  sm: 8,
  md: 12, // element-gap
  lg: 16, // card-gap
  xl: 24, // screen-padding
  xxl: 32,
  screenPadding: 24,
  cardGap: 16,
  elementGap: 12,
  touchTarget: 48,
} as const;

export const radius = {
  sm: 4,
  md: 12, // buttons + inputs ("actionable items")
  lg: 16, // cards + containers ("content containers")
  xl: 24,
  pill: 9999,
} as const;

/** Level 1 card shadow: offset 0,4 / blur 12 / 6% black (design.md Elevation). */
export const elevation = {
  card: {
    shadowColor: '#11252B',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.06,
    shadowRadius: 12,
    elevation: 2, // Android
  },
  raised: {
    shadowColor: '#11252B',
    shadowOffset: { width: 0, height: 6 },
    shadowOpacity: 0.12,
    shadowRadius: 16,
    elevation: 6,
  },
} as const;
