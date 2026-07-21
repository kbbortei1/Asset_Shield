import Constants from 'expo-constants';
import { router } from 'expo-router';
import { useState } from 'react';
import { Alert, Platform, Pressable, View } from 'react-native';
import { isApiError, ReportCategory, usersApi } from '@/lib/api';
import { Button, Header, Input, Screen, Text, useToast } from '@/components/ui';
import { colors, radius, spacing } from '@/theme';

const CATEGORIES: { value: ReportCategory; label: string }[] = [
  { value: 'BUG', label: 'Something broke' },
  { value: 'PAYMENT', label: 'Payment' },
  { value: 'ACCOUNT', label: 'My account' },
  { value: 'SUGGESTION', label: 'Suggestion' },
  { value: 'OTHER', label: 'Other' },
];

/** Report a problem you're facing in the app — routed to the admin queue. */
export default function ReportProblem() {
  const { show } = useToast();
  const [category, setCategory] = useState<ReportCategory>('BUG');
  const [message, setMessage] = useState('');
  const [busy, setBusy] = useState(false);

  const submit = async () => {
    if (message.trim().length < 5) {
      Alert.alert('Add a little more', 'Please describe the problem so we can help.');
      return;
    }
    setBusy(true);
    try {
      const version = Constants.expoConfig?.version ?? '';
      await usersApi.reportProblem({
        category,
        message: message.trim(),
        context: `${Platform.OS}${version ? ` · v${version}` : ''}`,
      });
      show('Thanks — your report was sent');
      router.back();
    } catch (e) {
      Alert.alert('Could not send', isApiError(e) ? e.message : 'Please try again.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <Screen footer={<Button title="Send report" loading={busy} disabled={message.trim().length < 5} onPress={submit} />}>
      <Header title="Report a problem" />
      <Text variant="bodyMd" color={colors.textMuted}>
        Tell us what went wrong or what could be better. Our team reviews every report.
      </Text>

      <View style={{ gap: spacing.sm }}>
        <Text variant="labelMd" color={colors.textMuted}>
          What's it about?
        </Text>
        <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: spacing.sm }}>
          {CATEGORIES.map((c) => {
            const active = category === c.value;
            return (
              <Pressable
                key={c.value}
                onPress={() => setCategory(c.value)}
                style={{
                  paddingHorizontal: spacing.md,
                  paddingVertical: spacing.sm,
                  borderRadius: radius.xl,
                  borderWidth: 1,
                  borderColor: active ? colors.primary : colors.border,
                  backgroundColor: active ? colors.primary : 'transparent',
                }}
              >
                <Text variant="labelMd" weight="semibold" color={active ? colors.onPrimary : colors.textMuted}>
                  {c.label}
                </Text>
              </Pressable>
            );
          })}
        </View>
      </View>

      <Input
        label="Describe the problem"
        value={message}
        onChangeText={setMessage}
        placeholder="What happened? What were you trying to do?"
        multiline
        numberOfLines={6}
        maxLength={2000}
        style={{ height: undefined, minHeight: 140, paddingTop: spacing.md, textAlignVertical: 'top' }}
      />
    </Screen>
  );
}
