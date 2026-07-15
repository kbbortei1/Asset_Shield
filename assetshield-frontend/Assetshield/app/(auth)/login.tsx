import { router } from 'expo-router';
import { useState } from 'react';
import { Pressable, View } from 'react-native';
import { isApiError } from '@/lib/api';
import { useAuth } from '@/lib/auth/AuthProvider';
import { validateAuthFields } from '@/lib/auth/validation';
import { Logo } from '@/components/brand/Logo';
import { AnimatedItem, Button, Header, Input, PhoneInput, Screen, Text } from '@/components/ui';
import { colors, spacing } from '@/theme';

/** Dedicated login (returning user). On-brand, contract-wired. */
export default function Login() {
  const { login } = useAuth();
  const [phoneNumber, setPhoneNumber] = useState('+233');
  const [password, setPassword] = useState('');
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const submit = async () => {
    setFormError(null);
    // Validate locally first: instant inline errors instead of a server round-trip.
    const clientErrors = validateAuthFields({ phoneNumber, password });
    setErrors(clientErrors);
    if (Object.keys(clientErrors).length > 0) return;

    setLoading(true);
    try {
      await login({ phoneNumber, password });
      // success: root gate redirects to (app).
    } catch (e) {
      if (isApiError(e)) {
        if (e.code === 'OTP_REQUIRED') {
          router.push(`/(auth)/otp?phone=${encodeURIComponent(phoneNumber)}` as never);
          return;
        }
        if (e.code === 'BAD_CREDENTIALS') setFormError('Phone or password is incorrect.');
        else if (e.fieldErrors) setErrors(e.fieldErrors);
        else setFormError(e.message);
      } else setFormError('Something went wrong. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Screen>
      <Header showBack={router.canGoBack()} />
      <AnimatedItem index={0}>
        <View style={{ alignItems: 'center', marginVertical: spacing.lg }}>
          <Logo size={72} />
        </View>
      </AnimatedItem>
      <AnimatedItem index={1}>
        <View style={{ gap: spacing.sm }}>
          <Text variant="headlineLgMobile">Welcome back</Text>
          <Text variant="bodyMd" color={colors.textMuted}>
            Log in to your AssetShield account.
          </Text>
        </View>
      </AnimatedItem>

      <AnimatedItem index={2}>
        <View style={{ gap: spacing.lg, marginTop: spacing.sm }}>
          <PhoneInput value={phoneNumber} onChangeText={setPhoneNumber} error={errors.phoneNumber} />
          <Input label="Password" value={password} onChangeText={setPassword} secureTextEntry error={errors.password} />
          <Pressable
            accessibilityRole="button"
            onPress={() => router.push('/(auth)/forgot-password' as never)}
            style={{ alignSelf: 'flex-end' }}
            hitSlop={8}
          >
            <Text variant="labelMd" color={colors.primary}>
              Forgot password?
            </Text>
          </Pressable>
          {formError ? (
            <Text variant="labelMd" color={colors.error}>
              {formError}
            </Text>
          ) : null}
        </View>
      </AnimatedItem>

      <AnimatedItem index={3}>
        <View style={{ gap: spacing.md, marginTop: spacing.md }}>
          <Button title="Log in" loading={loading} onPress={submit} />
          <Button title="Create an account" variant="ghost" onPress={() => router.replace('/(auth)/role' as never)} />
        </View>
      </AnimatedItem>
    </Screen>
  );
}
