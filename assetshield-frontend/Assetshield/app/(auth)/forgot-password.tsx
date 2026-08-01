import { router } from 'expo-router';
import { useState } from 'react';
import { View } from 'react-native';
import { authApi, isApiError } from '@/lib/api';
import { validateAuthFields } from '@/lib/auth/validation';
import { Button, Header, Input, PhoneInput, Screen, Text, useToast } from '@/components/ui';
import { colors, spacing } from '@/theme';

/**
 * Password reset: request an SMS code, then set a new password with it.
 * Step 1 (phone) and step 2 (code + new password) live on one screen.
 */
export default function ForgotPassword() {
  const { show } = useToast();
  const [step, setStep] = useState<'phone' | 'reset'>('phone');
  const [phoneNumber, setPhoneNumber] = useState('+233');
  const [code, setCode] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const requestCode = async () => {
    setFormError(null);
    const clientErrors = validateAuthFields({ phoneNumber });
    setErrors(clientErrors);
    if (Object.keys(clientErrors).length > 0) return;

    setLoading(true);
    try {
      await authApi.forgotPassword(phoneNumber);
      setStep('reset');
      show('If this number has an account, a code is on its way.');
    } catch (e) {
      if (isApiError(e) && e.code === 'OTP_THROTTLED') setFormError('Please wait a moment before requesting another code.');
      else setFormError(isApiError(e) ? e.message : 'Something went wrong. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  const reset = async () => {
    setFormError(null);
    const clientErrors = validateAuthFields({ code, password: newPassword }, { passwordMinLength: true });
    setErrors(clientErrors);
    if (Object.keys(clientErrors).length > 0) return;

    setLoading(true);
    try {
      await authApi.resetPassword({ phoneNumber, code, newPassword });
      show('Password updated. Log in with your new password.');
      router.replace('/(auth)/login' as never);
    } catch (e) {
      if (isApiError(e)) {
        if (e.code === 'OTP_INVALID') setErrors({ code: 'That code is incorrect. Please try again.' });
        else if (e.code === 'OTP_EXPIRED') setErrors({ code: 'That code expired. Request a new one.' });
        else if (e.fieldErrors) setErrors(e.fieldErrors);
        else setFormError(e.message);
      } else setFormError('Something went wrong. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Screen>
      <Header />
      <View style={{ gap: spacing.sm }}>
        <Text variant="headlineLgMobile">Reset your password</Text>
        <Text variant="bodyMd" color={colors.textMuted}>
          {step === 'phone'
            ? "Enter your phone number and we'll email you a reset code."
            : 'Enter the code we sent to your email and choose a new password.'}
        </Text>
      </View>

      <View style={{ gap: spacing.lg, marginTop: spacing.sm }}>
        {step === 'phone' ? (
          <PhoneInput value={phoneNumber} onChangeText={setPhoneNumber} error={errors.phoneNumber} autoFocus />
        ) : (
          <>
            <Input
              label="Reset code"
              value={code}
              onChangeText={(t) => setCode(t.replace(/[^0-9]/g, '').slice(0, 6))}
              keyboardType="number-pad"
              maxLength={6}
              placeholder="123456"
              error={errors.code}
              autoFocus
              autoComplete="sms-otp"
              textContentType="oneTimeCode"
            />
            <Input
              label="New password"
              value={newPassword}
              onChangeText={setNewPassword}
              secureTextEntry
              placeholder="At least 8 characters"
              error={errors.password ?? errors.newPassword}
              autoComplete="new-password"
              textContentType="newPassword"
              returnKeyType="done"
              onSubmitEditing={reset}
            />
          </>
        )}
        {formError ? (
          <Text variant="labelMd" color={colors.error}>
            {formError}
          </Text>
        ) : null}
      </View>

      <View style={{ gap: spacing.md, marginTop: spacing.md }}>
        {step === 'phone' ? (
          <Button title="Send reset code" loading={loading} onPress={requestCode} />
        ) : (
          <>
            <Button title="Set new password" loading={loading} onPress={reset} />
            <Button title="Resend code" variant="ghost" onPress={requestCode} />
          </>
        )}
      </View>
    </Screen>
  );
}
