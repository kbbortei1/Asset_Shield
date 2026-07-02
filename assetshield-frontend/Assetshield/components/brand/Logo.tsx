import { Image } from 'expo-image';
import { View } from 'react-native';
import { Text } from '@/components/ui';
import { colors } from '@/theme';

const EMBLEM = require('@/assets/images/logo-emblem.png');

/**
 * AssetShield GH brand mark: the real shield emblem (background trimmed) plus the
 * wordmark.
 */
export function Logo({ size = 72, showWordmark = true }: { size?: number; showWordmark?: boolean }) {
  return (
    <View style={{ alignItems: 'center', gap: 12 }}>
      <Image source={EMBLEM} style={{ width: size, height: size }} contentFit="contain" />
      {showWordmark ? (
        <View style={{ alignItems: 'center' }}>
          <Text variant="headlineMd" color={colors.primary}>
            AssetShield
          </Text>
          <Text variant="labelMd" color={colors.textMuted}>
            GHANA
          </Text>
        </View>
      ) : null}
    </View>
  );
}
