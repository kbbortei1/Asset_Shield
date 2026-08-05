import { router } from 'expo-router';
import { useState } from 'react';
import { View } from 'react-native';

import { isApiError, usersApi } from '@/lib/api';
import { Button, Header, Input, Screen, Text, useToast, showAlert } from '@/components/ui';
import { colors, spacing } from '@/theme';

/** Admin: create another admin (MISSING_DESIGN, on-brand). */
export default function CreateAdmin() {
  const [form, setForm] = useState({ fullName: '', phoneNumber: '+233', password: '' });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(false);
  const { show } = useToast();
  const set = (k: keyof typeof form) => (v: string) => setForm((f) => ({ ...f, [k]: v }));

  const submit = async () => {
    setErrors({});
    setLoading(true);
    try {
      await usersApi.createAdmin(form);
      show(`${form.fullName} can now sign in as an admin`);
      router.back();
    } catch (e) {
      if (isApiError(e)) {
        if (e.code === 'PHONE_EXISTS') setErrors({ phoneNumber: 'This number already has an account.' });
        else if (e.fieldErrors) setErrors(e.fieldErrors);
        else showAlert('Could not create', e.message);
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <Screen>
      <Header title="Create admin" />
      <Text variant="bodyMd" color={colors.textMuted}>
        Admins can verify agents and manage the platform. Grant carefully.
      </Text>
      <View style={{ gap: spacing.lg }}>
        <Input label="Full name" value={form.fullName} onChangeText={set('fullName')} autoCapitalize="words" error={errors.fullName} />
        <Input label="Phone number" value={form.phoneNumber} onChangeText={set('phoneNumber')} keyboardType="phone-pad" error={errors.phoneNumber} />
        <Input label="Temporary password" value={form.password} onChangeText={set('password')} secureTextEntry error={errors.password} />
      </View>
      <Button title="Create admin" loading={loading} disabled={!form.fullName.trim()} onPress={submit} />
    </Screen>
  );
}
