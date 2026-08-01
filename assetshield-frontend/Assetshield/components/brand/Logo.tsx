import { Image } from 'expo-image';
import { View } from 'react-native';
import { Text } from '@/components/ui';
import { colors } from '@/theme';

/**
 * AssetShield GH brand mark: the real rendered shield emblem plus the wordmark.
 * Uses the halo-trimmed emblem (`logo-emblem-clean.png`) so the shield sits
 * cleanly on any background — the untrimmed original is kept as the stored
 * launcher/icon asset.
 */
const EMBLEM = require('@/assets/images/logo-emblem-clean.png');

export function Logo({ size = 72, showWordmark = true }: { size?: number; showWordmark?: boolean }) {
  return (
    <View style={{ alignItems: 'center', gap: size * 0.12 }}>
      <Image source={EMBLEM} style={{ width: size, height: size }} contentFit="contain" />
      {showWordmark ? (
        <View style={{ flexDirection: 'row', alignItems: 'flex-start', gap: 4 }}>
          <Text variant="headlineMd" color={colors.primary}>
            AssetShield
          </Text>
          <Text variant="labelMd" weight="semibold" color={colors.cta} style={{ fontSize: 13, marginTop: 3 }}>
            GH
          </Text>
        </View>
      ) : null}
    </View>
  );
}
