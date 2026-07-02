import { router } from 'expo-router';
import { useState } from 'react';
import { View } from 'react-native';
import { isApiError } from '@/lib/api';
import { useAuth } from '@/lib/auth/AuthProvider';
import { Logo } from '@/components/brand/Logo';
import { AnimatedItem, Button, Header, Input, Screen, Text } from '@/components/ui';
import { colors, spacing } from '@/theme';

/** MISSING_DESIGN: dedicated login (returning user). On-brand, contract-wired. */
export default function Login() {
  const { login } = useAuth();
  const [phoneNumber, setPhoneNumber] = useState('+233');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const submit = async () => {
    setError(null);
    setLoading(true);
    try {
      await login({ phoneNumber, password });
      // success → root gate redirects to (app).
    } catch (e) {
      if (isApiError(e)) {
        if (e.code === 'OTP_REQUIRED') {
          router.push(`/(auth)/otp?phone=${encodeURIComponent(phoneNumber)}` as never);
          return;
        }
        if (e.code === 'BAD_CREDENTIALS') setError('Phone or password is incorrect.');
        else setError(e.message);
      } else setError('Something went wrong. Please try again.');
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
          <Input label="Phone number" value={phoneNumber} onChangeText={setPhoneNumber} keyboardType="phone-pad" />
          <Input label="Password" value={password} onChangeText={setPassword} secureTextEntry />
          {error ? (
            <Text variant="labelMd" color={colors.error}>
              {error}
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
