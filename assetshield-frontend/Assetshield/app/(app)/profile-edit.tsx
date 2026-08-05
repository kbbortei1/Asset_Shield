import { useQueryClient } from '@tanstack/react-query';
import { router } from 'expo-router';
import { useState } from 'react';
import { View } from 'react-native';
import { usersApi } from '@/lib/api';
import { useAuth } from '@/lib/auth/AuthProvider';
import { Button, Header, Input, Screen, showAlert } from '@/components/ui';
import { spacing } from '@/theme';

/** Edit profile (MISSING_DESIGN, on-brand). */
export default function ProfileEdit() {
  const { user, refreshUser } = useAuth();
  const qc = useQueryClient();
  const [fullName, setFullName] = useState(user?.fullName ?? '');
  const [loading, setLoading] = useState(false);

  const save = async () => {
    setLoading(true);
    try {
      await usersApi.updateMe({ fullName });
      await refreshUser();
      qc.invalidateQueries({ queryKey: ['users', 'me'] });
      router.back();
    } catch (e: any) {
      showAlert('Could not save', e?.message ?? 'Try again.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Screen>
      <Header title="Edit profile" />
      <View style={{ gap: spacing.lg }}>
        <Input label="Full name" value={fullName} onChangeText={setFullName} autoCapitalize="words" />
        <Input label="Phone number" value={user?.phoneNumber ?? ''} editable={false} hint="Phone number can't be changed here." />
      </View>
      <Button title="Save changes" loading={loading} disabled={!fullName.trim()} onPress={save} />
    </Screen>
  );
}
