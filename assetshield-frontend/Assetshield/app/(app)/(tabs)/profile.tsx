import { Ionicons } from '@expo/vector-icons';
import { router } from 'expo-router';
import { Alert, Pressable, View } from 'react-native';
import { usersApi } from '@/lib/api';
import { useAuth } from '@/lib/auth/AuthProvider';
import { useTheme } from '@/lib/theme/ThemeProvider';
import { Button, Card, Screen, Text } from '@/components/ui';
import { colors, radius, spacing, ThemeName } from '@/theme';

/** Stitch: "Profile & Settings". */
const THEME_OPTIONS: { key: ThemeName; label: string; icon: keyof typeof Ionicons.glyphMap }[] = [
  { key: 'light', label: 'Light', icon: 'sunny' },
  { key: 'dark', label: 'Dark', icon: 'moon' },
  { key: 'gold', label: 'Gold', icon: 'diamond' },
];

export default function ProfileTab() {
  const { user, logout } = useAuth();
  const { theme, setTheme } = useTheme();
  const role = user?.role ?? 'OWNER';

  const confirmErasure = () =>
    Alert.alert(
      'Delete account?',
      'This requests permanent erasure of your account and data. This cannot be undone.',
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Request deletion',
          style: 'destructive',
          onPress: async () => {
            try {
              await usersApi.requestErasure();
              await logout();
            } catch {
              Alert.alert('Could not complete', 'Please try again later.');
            }
          },
        },
      ],
    );

  return (
    <Screen>
      <Card>
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: spacing.lg }}>
          <View style={{ width: 56, height: 56, borderRadius: 28, backgroundColor: colors.tealTint, alignItems: 'center', justifyContent: 'center' }}>
            <Ionicons name="person" size={28} color={colors.primary} />
          </View>
          <View style={{ flex: 1 }}>
            <Text variant="headlineSm">{user?.fullName ?? 'Your account'}</Text>
            <Text variant="labelMd" color={colors.textMuted}>
              {user?.phoneNumber} · {role}
            </Text>
          </View>
        </View>
      </Card>

      <View style={{ gap: spacing.sm }}>
        <Row icon="person-outline" label="Edit profile" onPress={() => router.push('/(app)/profile-edit' as never)} />
        <Row icon="card-outline" label="Ghana Card (KYC)" onPress={() => router.push('/(app)/kyc' as never)} />
        <Row icon="notifications-outline" label="Notification preferences" onPress={() => router.push('/(app)/notification-preferences' as never)} />
        {role === 'OWNER' || role === 'AGENT' ? (
          <Row icon="star-outline" label={role === 'AGENT' ? 'Subscription' : 'AssetShield PRO'} onPress={() => router.push('/(app)/subscription' as never)} />
        ) : null}
        <Row icon="receipt-outline" label="Billing history" onPress={() => router.push('/(app)/billing' as never)} />
        {role === 'ADMIN' ? <Row icon="person-add-outline" label="Create an admin" onPress={() => router.push('/(app)/admin/new' as never)} /> : null}
      </View>

      <Card>
        <Text variant="bodyMd" weight="semibold" style={{ marginBottom: spacing.md }}>
          Appearance
        </Text>
        <View style={{ flexDirection: 'row', gap: spacing.sm }}>
          {THEME_OPTIONS.map((opt) => {
            const active = theme === opt.key;
            return (
              <Pressable
                key={opt.key}
                onPress={() => setTheme(opt.key)}
                style={{
                  flex: 1,
                  alignItems: 'center',
                  gap: 4,
                  paddingVertical: spacing.md,
                  borderRadius: radius.md,
                  borderWidth: active ? 2 : 1,
                  borderColor: active ? colors.primary : colors.border,
                  backgroundColor: active ? colors.tealTint : colors.card,
                }}
              >
                <Ionicons name={opt.icon} size={20} color={active ? colors.primary : colors.textMuted} />
                <Text variant="labelMd" color={active ? colors.primary : colors.textMuted}>
                  {opt.label}
                </Text>
              </Pressable>
            );
          })}
        </View>
      </Card>

      <View style={{ gap: spacing.md, marginTop: spacing.md }}>
        <Button title="Log out" variant="secondary" onPress={() => logout()} />
        <Pressable onPress={confirmErasure} style={{ alignItems: 'center', paddingVertical: spacing.sm }}>
          <Text variant="labelMd" color={colors.error}>
            Delete my account
          </Text>
        </Pressable>
      </View>
    </Screen>
  );
}

function Row({ icon, label, onPress }: { icon: keyof typeof Ionicons.glyphMap; label: string; onPress: () => void }) {
  return (
    <Card onPress={onPress} padded>
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: spacing.md }}>
        <Ionicons name={icon} size={22} color={colors.primary} />
        <Text variant="bodyMd" style={{ flex: 1 }}>
          {label}
        </Text>
        <Ionicons name="chevron-forward" size={18} color={colors.textMuted} />
      </View>
    </Card>
  );
}
