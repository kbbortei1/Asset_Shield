import { Ionicons } from '@expo/vector-icons';
import { View, ViewStyle } from 'react-native';
import { colors, radius, spacing } from '@/theme';
import { Text } from './Text';

const pill: ViewStyle = {
  flexDirection: 'row',
  alignItems: 'center',
  alignSelf: 'flex-start',
  gap: spacing.xs,
  paddingHorizontal: spacing.md,
  paddingVertical: 4,
  borderRadius: radius.pill,
};

export type AssetStatus = 'secured' | 'needsUpdate' | 'damaged';

// Resolved at render (not module load) so it follows the active theme palette.
function statusMeta(status: AssetStatus): { bg: string; label: string } {
  switch (status) {
    case 'secured':
      return { bg: colors.statusSecuredBg, label: 'Secured' };
    case 'needsUpdate':
      return { bg: colors.statusNeedsUpdateBg, label: 'Needs Update' };
    case 'damaged':
      return { bg: colors.statusDamagedBg, label: 'Damaged' };
  }
}

/** Status pill — white text on a functional-color background. */
export function StatusBadge({ status, label }: { status: AssetStatus; label?: string }) {
  const s = statusMeta(status);
  return (
    <View style={[pill, { backgroundColor: s.bg }]}>
      <Text variant="labelMd" color={colors.white}>
        {label ?? s.label}
      </Text>
    </View>
  );
}

/** Tamper-evident badge: lock icon + truncated hex hash, teal-on-tint. */
export function VerifiedBadge({ hash }: { hash?: string | null }) {
  const short = hash ? `#${hash.slice(0, 4).toUpperCase()}…${hash.slice(-4).toUpperCase()}` : 'Verified';
  return (
    <View style={[pill, { backgroundColor: colors.tealTint }]}>
      <Ionicons name="lock-closed" size={12} color={colors.primary} />
      <Text variant="labelMd" color={colors.primary}>
        {short}
      </Text>
    </View>
  );
}

/** Value pill — ₵ amount with teal border + bold Sora text. */
export function ValuePill({ amount }: { amount: number }) {
  return (
    <View style={[pill, { borderWidth: 1, borderColor: colors.primary, backgroundColor: 'transparent' }]}>
      <Text variant="labelMd" weight="bold" color={colors.primary}>
        {formatCedis(amount)}
      </Text>
    </View>
  );
}

export function formatCedis(amount: number): string {
  return `₵${amount.toLocaleString('en-GH', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}
