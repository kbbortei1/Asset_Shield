import { router } from 'expo-router';
import { useState } from 'react';
import { View } from 'react-native';
import { isApiError } from '@/lib/api';
import { useAuth } from '@/lib/auth/AuthProvider';
import { validateAuthFields } from '@/lib/auth/validation';
import { Button, Header, Input, PhoneInput, Screen, Text } from '@/components/ui';
import { colors, spacing } from '@/theme';

/** Stitch: "Phone Sign-up" / "Sign Up & Verify" (owner path). */
export default function Register() {
  const { register } = useAuth();
  const [fullName, setFullName] = useState('');
  const [phoneNumber, setPhoneNumber] = useState('+233');
  const [password, setPassword] = useState('');
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const submit = async () => {
    setFormError(null);
    const clientErrors = validateAuthFields({ fullName, phoneNumber, password }, { passwordMinLength: true });
    setErrors(clientErrors);
    if (Object.keys(clientErrors).length > 0) return;
    setLoading(true);
    try {
      await register({ fullName, phoneNumber, password });
      router.push(`/(auth)/otp?phone=${encodeURIComponent(phoneNumber)}` as never);
    } catch (e) {
      if (isApiError(e)) {
        if (e.code === 'PHONE_EXISTS') {
          setFormError('This number already has an account. Please log in.');
        } else if (e.fieldErrors) {
          setErrors(e.fieldErrors);
        } else {
          setFormError(e.message);
        }
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
        <Text variant="headlineLgMobile">Create your account</Text>
        <Text variant="bodyMd" color={colors.textMuted}>
          We'll send a one-time code to verify your number.
        </Text>
      </View>

      <View style={{ gap: spacing.lg, marginTop: spacing.sm }}>
        <Input label="Full name" value={fullName} onChangeText={setFullName} placeholder="Akosua Owusu" error={errors.fullName} autoCapitalize="words" />
        <PhoneInput value={phoneNumber} onChangeText={setPhoneNumber} error={errors.phoneNumber} />
        <Input
          label="Password"
          value={password}
          onChangeText={setPassword}
          placeholder="At least 8 characters"
          secureTextEntry
          error={errors.password}
        />
        {formError ? (
          <Text variant="labelMd" color={colors.error}>
            {formError}
          </Text>
        ) : null}
      </View>

      <View style={{ gap: spacing.md, marginTop: spacing.md }}>
        <Button title="Send code" loading={loading} onPress={submit} />
        <Button title="I already have an account" variant="ghost" onPress={() => router.replace('/(auth)/login' as never)} />
      </View>
    </Screen>
  );
}
