import { Ionicons } from '@expo/vector-icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Pressable, View } from 'react-native';
import { notificationsApi, TipsFrequency } from '@/lib/api';
import { Card, ErrorState, Header, Loading, Screen, Text } from '@/components/ui';
import { colors, spacing } from '@/theme';

const OPTIONS: { value: TipsFrequency; label: string; hint: string }[] = [
  { value: 'DAILY', label: 'Daily', hint: 'A safety tip every day' },
  { value: 'WEEKLY', label: 'Weekly', hint: 'A digest once a week' },
  { value: 'OFF', label: 'Off', hint: 'No tip notifications' },
];

/** Notification preferences (tips frequency). */
export default function NotificationPreferences() {
  const qc = useQueryClient();
  const q = useQuery({ queryKey: ['notif-prefs'], queryFn: () => notificationsApi.getPreferences() });

  const update = useMutation({
    mutationFn: (tipsFrequency: TipsFrequency) => notificationsApi.updatePreferences({ tipsFrequency }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['notif-prefs'] }),
  });

  if (q.isLoading) return <Loading />;
  if (q.isError) return <ErrorState onRetry={() => q.refetch()} />;
  const current = q.data?.tipsFrequency ?? 'WEEKLY';

  return (
    <Screen>
      <Header title="Notifications" />
      <Text variant="bodyMd" color={colors.textMuted}>
        How often would you like to receive safety tips?
      </Text>
      <View style={{ gap: spacing.md }}>
        {OPTIONS.map((o) => {
          const active = o.value === current;
          return (
            <Pressable key={o.value} onPress={() => update.mutate(o.value)}>
              <Card style={{ borderWidth: active ? 2 : 1, borderColor: active ? colors.primary : colors.border }}>
                <View style={{ flexDirection: 'row', alignItems: 'center', gap: spacing.md }}>
                  <View style={{ flex: 1 }}>
                    <Text variant="bodyMd" weight="semibold">
                      {o.label}
                    </Text>
                    <Text variant="labelMd" color={colors.textMuted}>
                      {o.hint}
                    </Text>
                  </View>
                  <Ionicons name={active ? 'radio-button-on' : 'radio-button-off'} size={22} color={active ? colors.primary : colors.textMuted} />
                </View>
              </Card>
            </Pressable>
          );
        })}
      </View>
    </Screen>
  );
}
