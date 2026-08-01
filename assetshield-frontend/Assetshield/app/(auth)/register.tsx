import { Ionicons } from '@expo/vector-icons';
import { router } from 'expo-router';
import { useState } from 'react';
import { Pressable, View } from 'react-native';
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
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [agreed, setAgreed] = useState(false);
  const [loading, setLoading] = useState(false);

  const submit = async () => {
    setFormError(null);
    if (!agreed) {
      setFormError('Please agree to the Terms & Privacy Policy to continue.');
      return;
    }
    const clientErrors = validateAuthFields({ fullName, phoneNumber, email, password }, { passwordMinLength: true });
    setErrors(clientErrors);
    if (Object.keys(clientErrors).length > 0) return;
    setLoading(true);
    try {
      await register({ fullName, phoneNumber, email: email.trim(), password });
      router.push(`/(auth)/otp?phone=${encodeURIComponent(phoneNumber)}` as never);
    } catch (e) {
      if (isApiError(e)) {
        if (e.code === 'PHONE_EXISTS') {
          setFormError('This number already has an account. Please log in.');
        } else if (e.code === 'EMAIL_EXISTS') {
          setFormError('This email already has an account. Please log in.');
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
          We'll email you a one-time code to verify your account.
        </Text>
      </View>

      <View style={{ gap: spacing.lg, marginTop: spacing.sm }}>
        <Input
          label="Full name"
          value={fullName}
          onChangeText={setFullName}
          placeholder="Akosua Owusu"
          error={errors.fullName}
          autoCapitalize="words"
          autoComplete="name"
          textContentType="name"
          returnKeyType="next"
        />
        <PhoneInput value={phoneNumber} onChangeText={setPhoneNumber} error={errors.phoneNumber} returnKeyType="next" />
        <Input
          label="Email"
          value={email}
          onChangeText={setEmail}
          placeholder="you@example.com"
          error={errors.email}
          autoCapitalize="none"
          keyboardType="email-address"
          autoComplete="email"
          textContentType="emailAddress"
          returnKeyType="next"
        />
        <Input
          label="Password"
          value={password}
          onChangeText={setPassword}
          placeholder="At least 8 characters"
          secureTextEntry
          error={errors.password}
          autoComplete="new-password"
          textContentType="newPassword"
          returnKeyType="done"
          onSubmitEditing={submit}
        />
        {formError ? (
          <Text variant="labelMd" color={colors.error}>
            {formError}
          </Text>
        ) : null}
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
        <Button title="I already have an account" variant="ghost" onPress={() => router.replace('/(auth)/login' as never)} />
      </View>
    </Screen>
  );
}
