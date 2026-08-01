import { Ionicons } from '@expo/vector-icons';
import { router } from 'expo-router';
import { useState } from 'react';
import { Pressable, View } from 'react-native';
import { isApiError } from '@/lib/api';
import { useAuth } from '@/lib/auth/AuthProvider';
import { validateAuthFields } from '@/lib/auth/validation';
import { Button, Card, Header, Input, PhoneInput, Screen, Text } from '@/components/ui';
import { colors, spacing } from '@/theme';

/** Stitch: "Agent Registration & Verification". Agents start PENDING_VERIFICATION. */
export default function RegisterAgent() {
  const { registerAgent } = useAuth();
  const [form, setForm] = useState({
    fullName: '',
    phoneNumber: '+233',
    email: '',
    password: '',
    insurerName: '',
    nicLicenceNo: '',
  });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [agreed, setAgreed] = useState(false);
  const [loading, setLoading] = useState(false);

  const set = (k: keyof typeof form) => (v: string) => setForm((f) => ({ ...f, [k]: v }));

  const submit = async () => {
    setFormError(null);
    if (!agreed) {
      setFormError('Please agree to the Terms & Privacy Policy to continue.');
      return;
    }
    const clientErrors = validateAuthFields(
      {
        fullName: form.fullName,
        phoneNumber: form.phoneNumber,
        email: form.email,
        password: form.password,
        insurerName: form.insurerName,
        nicLicenceNo: form.nicLicenceNo,
      },
      { passwordMinLength: true },
    );
    setErrors(clientErrors);
    if (Object.keys(clientErrors).length > 0) return;
    setLoading(true);
    try {
      await registerAgent({ ...form, email: form.email.trim() });
      router.push(`/(auth)/otp?phone=${encodeURIComponent(form.phoneNumber)}` as never);
    } catch (e) {
      if (isApiError(e)) {
        if (e.code === 'PHONE_EXISTS') setFormError('This number already has an account. Please log in.');
        else if (e.code === 'EMAIL_EXISTS') setFormError('This email already has an account. Please log in.');
        else if (e.code === 'LICENCE_EXISTS') setErrors({ nicLicenceNo: 'This licence number is already registered.' });
        else if (e.fieldErrors) setErrors(e.fieldErrors);
        else setFormError(e.message);
      } else {
        setFormError('Something went wrong. Please try again.');
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <Screen>
      <Header />
      <View style={{ gap: spacing.sm }}>
        <Text variant="headlineLgMobile">Agent registration</Text>
        <Text variant="bodyMd" color={colors.textMuted}>
          After you verify your email, an admin reviews your licence before you can access leads.
        </Text>
      </View>

      <View style={{ gap: spacing.lg, marginTop: spacing.sm }}>
        <Input label="Full name" value={form.fullName} onChangeText={set('fullName')} autoCapitalize="words" error={errors.fullName} />
        <PhoneInput value={form.phoneNumber} onChangeText={set('phoneNumber')} error={errors.phoneNumber} />
        <Input label="Email" value={form.email} onChangeText={set('email')} placeholder="you@example.com" autoCapitalize="none" keyboardType="email-address" autoComplete="email" textContentType="emailAddress" error={errors.email} />
        <Input label="Password" value={form.password} onChangeText={set('password')} secureTextEntry error={errors.password} />
        <Input label="Insurer" value={form.insurerName} onChangeText={set('insurerName')} placeholder="e.g. Hollard Ghana" error={errors.insurerName} />
        <Input label="NIC licence number" value={form.nicLicenceNo} onChangeText={set('nicLicenceNo')} placeholder="NIC-12345" autoCapitalize="characters" error={errors.nicLicenceNo} />
        {formError ? (
          <Text variant="labelMd" color={colors.error}>
            {formError}
          </Text>
        ) : null}
        <Card style={{ backgroundColor: colors.tealTint }}>
          <Text variant="labelMd" color={colors.primary}>
            Your account stays in review until an admin verifies your NIC licence. You'll be notified when approved.
          </Text>
        </Card>
      </View>

      <Pressable
        onPress={() => setAgreed((a) => !a)}
        style={{ flexDirection: 'row', gap: spacing.sm, alignItems: 'flex-start', marginTop: spacing.md }}
      >
        <Ionicons name={agreed ? 'checkbox' : 'square-outline'} size={22} color={agreed ? colors.primary : colors.textMuted} />
        <Text variant="labelMd" color={colors.textMuted} style={{ flex: 1 }}>
          I agree to the{' '}
          <Text variant="labelMd" weight="semibold" color={colors.primary} onPress={() => router.push('/(auth)/terms' as never)}>
            Terms & Privacy Policy
          </Text>
          , including how my photos are stored.
        </Text>
      </Pressable>

      <View style={{ gap: spacing.md, marginTop: spacing.md }}>
        <Button title="Send code" loading={loading} disabled={!agreed} onPress={submit} />
      </View>
    </Screen>
  );
}
