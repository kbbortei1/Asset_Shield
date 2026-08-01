import { useLocalSearchParams } from 'expo-router';
import { useEffect, useRef, useState } from 'react';
import { View } from 'react-native';
import { isApiError } from '@/lib/api';
import { useAuth } from '@/lib/auth/AuthProvider';
import { Button, Header, Input, Screen, Text } from '@/components/ui';
import { colors, spacing } from '@/theme';

/** Stitch: OTP step of "Sign Up & Verify". Dev code is 123456 (FCM_MODE=log). */
export default function Otp() {
  const { phone } = useLocalSearchParams<{ phone?: string }>();
  const phoneNumber = phone ?? '';
  const { verifyOtp, resendOtp } = useAuth();
  const [code, setCode] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [cooldown, setCooldown] = useState(0);
  const timer = useRef<ReturnType<typeof setInterval> | null>(null);

  useEffect(() => {
    if (cooldown <= 0) return;
    timer.current = setInterval(() => setCooldown((c) => Math.max(0, c - 1)), 1000);
    return () => {
      if (timer.current) clearInterval(timer.current);
    };
  }, [cooldown]);

  const verify = async () => {
    setError(null);
    setLoading(true);
    try {
      await verifyOtp({ phoneNumber, code });
      // success → AuthProvider flips to authenticated → root gate redirects.
    } catch (e) {
      if (isApiError(e)) {
        if (e.code === 'OTP_INVALID') setError('That code is incorrect. Please try again.');
        else if (e.code === 'OTP_EXPIRED') setError('That code expired. Request a new one.');
        else if (e.code === 'OTP_THROTTLED') {
          setError('Too many attempts. Please wait a moment.');
          setCooldown(30);
        } else setError(e.message);
      } else setError('Something went wrong. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  const resend = async () => {
    setError(null);
    try {
      await resendOtp(phoneNumber);
      setCooldown(30);
    } catch (e) {
      if (isApiError(e) && e.code === 'OTP_THROTTLED') {
        setCooldown(30);
        setError('Please wait before requesting another code.');
      } else setError('Could not resend the code.');
    }
  };

  return (
    <Screen>
      <Header />
      <View style={{ gap: spacing.sm }}>
        <Text variant="headlineLgMobile">Enter your code</Text>
        <Text variant="bodyMd" color={colors.textMuted}>
          We sent a 6-digit code to your email. Enter it below to verify your account.
        </Text>
      </View>

      <View style={{ gap: spacing.lg, marginTop: spacing.lg }}>
        <Input
          label="Verification code"
          value={code}
          onChangeText={(t) => setCode(t.replace(/[^0-9]/g, '').slice(0, 6))}
          placeholder="123456"
          keyboardType="number-pad"
          error={error}
          maxLength={6}
          autoFocus
          autoComplete="sms-otp"
          textContentType="oneTimeCode"
        />
        <Button title="Verify" loading={loading} disabled={code.length < 6} onPress={verify} />
        <Button
          title={cooldown > 0 ? `Resend code in ${cooldown}s` : 'Resend code'}
          variant="ghost"
          disabled={cooldown > 0}
          onPress={resend}
        />
      </View>
    </Screen>
  );
}
