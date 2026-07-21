import { Ionicons } from '@expo/vector-icons';
import { Pressable, View } from 'react-native';
import Svg, { Defs, LinearGradient, Path, Stop } from 'react-native-svg';
import { colors, radius, spacing } from '@/theme';
import { Text } from './Text';

/** Same silhouette as the brand mark, so every action reads as "protected". */
const SHIELD_PATH = 'M50 4 L90 17 L90 49 C90 73 73 90 50 98 C27 90 10 73 10 49 L10 17 Z';

export type ActionTileProps = {
  icon: keyof typeof Ionicons.glyphMap;
  label: string;
  onPress: () => void;
  /** Muted styling for a secondary/disabled-looking action. */
  muted?: boolean;
};

/**
 * Compact quick-action tile: the icon sits inside a shield badge echoing the
 * AssetShield mark, with a single-line label underneath.
 *
 * The label is locked to one line and shrinks to fit — three tiles per row on a
 * narrow phone is only ~100dp wide, which used to break words mid-character
 * ("Househo / ld", "Analytic / s").
 */
export function ActionTile({ icon, label, onPress, muted }: ActionTileProps) {
  const badge = 44;
  const tint = muted ? colors.textMuted : colors.primary;
  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="button"
      accessibilityLabel={label}
      style={{ flex: 1 }}
    >
      {({ pressed }) => (
        <View
          style={{
            alignItems: 'center',
            justifyContent: 'center',
            gap: spacing.xs,
            paddingVertical: spacing.md,
            paddingHorizontal: spacing.xs,
            minHeight: 96,
            borderRadius: radius.lg,
            borderWidth: 1,
            borderColor: colors.border,
            backgroundColor: pressed ? colors.tealTint : colors.card,
          }}
        >
          <View style={{ width: badge, height: badge, alignItems: 'center', justifyContent: 'center' }}>
            <Svg width={badge} height={badge} viewBox="0 0 100 100" style={{ position: 'absolute' }}>
              <Defs>
                <LinearGradient id="tileShield" x1="0" y1="0" x2="0" y2="1">
                  <Stop offset="0" stopColor={colors.tealTint} stopOpacity="1" />
                  <Stop offset="1" stopColor={colors.tealTint} stopOpacity="0.55" />
                </LinearGradient>
              </Defs>
              <Path
                d={SHIELD_PATH}
                fill="url(#tileShield)"
                stroke={tint}
                strokeOpacity={0.35}
                strokeWidth={4}
                strokeLinejoin="round"
              />
            </Svg>
            <Ionicons name={icon} size={19} color={tint} />
          </View>

          <Text
            variant="labelMd"
            color={colors.textMuted}
            numberOfLines={1}
            adjustsFontSizeToFit
            minimumFontScale={0.7}
            style={{ textAlign: 'center' }}
          >
            {label}
          </Text>
        </View>
      )}
    </Pressable>
  );
}
