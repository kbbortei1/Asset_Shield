import { Ionicons } from '@expo/vector-icons';
import { router } from 'expo-router';
import { useState } from 'react';
import { ActivityIndicator, Alert, Pressable, View } from 'react-native';
import { usersApi } from '@/lib/api';
import { useAuth } from '@/lib/auth/AuthProvider';
import { buildFileForm, PermissionError, pickImage } from '@/lib/media/capture';
import { useOffline } from '@/lib/offline/OfflineProvider';
import { useTheme } from '@/lib/theme/ThemeProvider';
import { Button, Card, RemoteImage, Screen, SectionHeader, Text, useActionSheet, useConfirm, useToast } from '@/components/ui';
import { colors, radius, spacing, ThemeName } from '@/theme';

/** Stitch: "Profile & Settings". */
const THEME_OPTIONS: { key: ThemeName; label: string; icon: keyof typeof Ionicons.glyphMap }[] = [
  { key: 'light', label: 'Light', icon: 'sunny' },
  { key: 'dark', label: 'Dark', icon: 'moon' },
  { key: 'gold', label: 'Gold', icon: 'diamond' },
];

export default function ProfileTab() {
  const { user, logout, refreshUser } = useAuth();
  const { theme, setTheme } = useTheme();
  const { failed, retryFailedNow } = useOffline();
  const { show } = useToast();
  const confirm = useConfirm();
  const showActions = useActionSheet();
  const role = user?.role ?? 'OWNER';
  const [avatarBusy, setAvatarBusy] = useState(false);

  const onRetryFailed = async () => {
    await retryFailedNow();
    show('Retrying failed uploads…');
  };

  const uploadAvatar = async (source: 'camera' | 'library') => {
    if (avatarBusy) return;
    let img;
    try {
      img = await pickImage(source);
    } catch (e) {
      if (e instanceof PermissionError) {
        Alert.alert('Permission needed', `Allow ${e.kind} access to update your photo.`);
      }
      return;
    }
    if (!img) return;
    setAvatarBusy(true);
    try {
      await usersApi.uploadAvatar(buildFileForm(img));
      await refreshUser();
      show('Profile picture updated');
    } catch (e: any) {
      Alert.alert('Could not update', e?.message ?? 'Please try again.');
    } finally {
      setAvatarBusy(false);
    }
  };

  // Designed chooser (not the generic OS alert): take a fresh photo or pick an
  // existing one — their own image either way (there are no preset avatars).
  const changeAvatar = async () => {
    const choice = await showActions({
      title: 'Profile picture',
      message: 'Upload a photo of yourself.',
      options: [
        { label: 'Take a photo', value: 'camera', icon: 'camera' },
        { label: 'Choose from gallery', value: 'library', icon: 'images' },
      ],
    });
    if (choice === 'camera' || choice === 'library') uploadAvatar(choice);
  };

  const confirmLogout = async () => {
    const { confirmed } = await confirm({
      title: 'Log out?',
      message: 'You’ll need your phone number and password to sign back in.',
      confirmLabel: 'Log out',
      icon: 'log-out-outline',
    });
    if (confirmed) await logout();
  };

  // Irreversible → password-gated: confirm → re-auth → delete. A wrong password
  // re-opens the sheet with an inline error.
  const confirmErasure = async () => {
    let passwordError: string | undefined;
    for (;;) {
      const { confirmed, password } = await confirm({
        title: 'Delete account?',
        message:
          'Your account is deactivated immediately: your login stops working and your phone number is freed up. This cannot be undone.',
        confirmLabel: 'Delete my account',
        destructive: true,
        icon: 'trash-outline',
        requirePassword: true,
        passwordError,
      });
      if (!confirmed) return;
      try {
        const ok = await usersApi.verifyPassword(password ?? '');
        if (!ok) {
          passwordError = 'Incorrect password. Please try again.';
          continue;
        }
      } catch {
        Alert.alert('Could not verify', 'Please check your connection and try again.');
        return;
      }
      try {
        await usersApi.requestErasure();
        await logout();
      } catch {
        Alert.alert('Could not complete', 'Please try again later.');
      }
      return;
    }
  };

  return (
    <Screen>
      <Card>
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: spacing.lg }}>
          <Pressable onPress={changeAvatar} accessibilityRole="button" accessibilityLabel="Change profile picture">
            <View style={{ width: 64, height: 64, borderRadius: 32, overflow: 'hidden', backgroundColor: colors.tealTint, alignItems: 'center', justifyContent: 'center' }}>
              {user?.avatarUrl ? (
                <RemoteImage uri={user.avatarUrl} width={64} height={64} radius={32} />
              ) : (
                <Ionicons name="person" size={30} color={colors.primary} />
              )}
              {avatarBusy ? (
                <View style={{ position: 'absolute', inset: 0, backgroundColor: 'rgba(0,0,0,0.35)', alignItems: 'center', justifyContent: 'center' }}>
                  <ActivityIndicator color={colors.white} />
                </View>
              ) : null}
            </View>
            {/* camera badge */}
            <View style={{ position: 'absolute', right: -2, bottom: -2, width: 24, height: 24, borderRadius: 12, backgroundColor: colors.primary, alignItems: 'center', justifyContent: 'center', borderWidth: 2, borderColor: colors.card }}>
              <Ionicons name="camera" size={12} color={colors.white} />
            </View>
          </Pressable>
          <View style={{ flex: 1 }}>
            <Text variant="headlineSm">{user?.fullName ?? 'Your account'}</Text>
            <Text variant="labelMd" color={colors.textMuted}>
              {user?.phoneNumber} · {role}
            </Text>
          </View>
        </View>
      </Card>

      {failed > 0 ? (
        <Card style={{ backgroundColor: colors.warning }}>
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: spacing.md }}>
            <Ionicons name="cloud-offline" size={22} color={colors.white} />
            <View style={{ flex: 1 }}>
              <Text variant="bodyMd" weight="semibold" color={colors.white}>
                {failed} upload{failed === 1 ? '' : 's'} failed
              </Text>
              <Text variant="labelMd" color={colors.white}>
                These were rejected by the server and stopped retrying.
              </Text>
            </View>
            <Button title="Retry" fullWidth={false} variant="secondary" onPress={onRetryFailed} />
          </View>
        </Card>
      ) : null}

      <SectionHeader title="Account" />
      <View style={{ gap: spacing.sm }}>
        <Row icon="person-outline" label="Edit profile" onPress={() => router.push('/(app)/profile-edit' as never)} />
        {role !== 'ADMIN' ? <Row icon="card-outline" label="Ghana Card (KYC)" onPress={() => router.push('/(app)/kyc' as never)} /> : null}
        {/* Admins don't receive user notifications — they broadcast them. */}
        {role !== 'ADMIN' ? <Row icon="notifications-outline" label="Notification preferences" onPress={() => router.push('/(app)/notification-preferences' as never)} /> : null}
        {role === 'OWNER' || role === 'AGENT' ? (
          <Row icon="star-outline" label={role === 'AGENT' ? 'Subscription' : 'AssetShield PRO'} onPress={() => router.push('/(app)/subscription' as never)} />
        ) : null}
        {role !== 'ADMIN' ? <Row icon="receipt-outline" label="Billing history" onPress={() => router.push('/(app)/billing' as never)} /> : null}
        <Row icon="lock-closed-outline" label="Privacy & data" onPress={() => router.push('/(app)/privacy' as never)} />
        <Row icon="chatbox-ellipses-outline" label="Report a problem" onPress={() => router.push('/(app)/report-problem' as never)} />
      </View>

      {role === 'ADMIN' ? (
        <>
          <SectionHeader title="Admin" />
          <View style={{ gap: spacing.sm }}>
            <Row icon="megaphone-outline" label="Broadcast a notification" onPress={() => router.push('/(app)/admin/broadcast' as never)} />
            <Row icon="chatbox-ellipses-outline" label="Problem reports" onPress={() => router.push('/(app)/admin/reports' as never)} />
            <Row icon="shield-outline" label="Audit trail" onPress={() => router.push('/(app)/admin/audit' as never)} />
            <Row icon="person-add-outline" label="Create an admin" onPress={() => router.push('/(app)/admin/new' as never)} />
          </View>
        </>
      ) : null}

      <SectionHeader title="Preferences" />
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
        <Button title="Log out" variant="secondary" onPress={confirmLogout} />
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
