import { Ionicons } from '@expo/vector-icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Pressable, Switch, View } from 'react-native';
import { NotificationPreferences, notificationsApi, TipsFrequency } from '@/lib/api';
import { Card, ErrorState, Header, Loading, Screen, SectionHeader, Text, useToast } from '@/components/ui';
import { colors, spacing } from '@/theme';

const OPTIONS: { value: TipsFrequency; label: string; hint: string }[] = [
  { value: 'DAILY', label: 'Daily', hint: 'A safety tip every day' },
  { value: 'WEEKLY', label: 'Weekly', hint: 'A digest once a week' },
  { value: 'OFF', label: 'Off', hint: 'No tip notifications' },
];

/** Notification preferences — delivery channels + tips frequency. */
export default function NotificationPreferencesScreen() {
  const qc = useQueryClient();
  const { show } = useToast();
  const q = useQuery({ queryKey: ['notif-prefs'], queryFn: () => notificationsApi.getPreferences() });

  const update = useMutation({
    mutationFn: (patch: Partial<NotificationPreferences>) => notificationsApi.updatePreferences(patch),
    // optimistic: reflect the toggle immediately, roll back on failure
    onMutate: async (patch) => {
      await qc.cancelQueries({ queryKey: ['notif-prefs'] });
      const prev = qc.getQueryData<NotificationPreferences>(['notif-prefs']);
      if (prev) qc.setQueryData(['notif-prefs'], { ...prev, ...patch });
      return { prev };
    },
    onError: (_e, _patch, ctx) => {
      if (ctx?.prev) qc.setQueryData(['notif-prefs'], ctx.prev);
      show('Could not save. Try again.');
    },
    onSettled: () => qc.invalidateQueries({ queryKey: ['notif-prefs'] }),
  });

  if (q.isLoading) return <Loading />;
  if (q.isError) return <ErrorState onRetry={() => q.refetch()} />;
  const prefs = q.data!;

  return (
    <Screen>
      <Header title="Notifications" />

      <SectionHeader title="How you get alerts" />
      <Card>
        <ChannelRow
          icon="phone-portrait-outline"
          title="In-app alerts"
          hint="Show updates in the Alerts tab (dossiers, quotes, reminders)."
          value={prefs.inAppEnabled}
          onValueChange={(v) => update.mutate({ inAppEnabled: v })}
        />
        <View style={{ height: 1, backgroundColor: colors.border, marginVertical: spacing.md }} />
        <ChannelRow
          icon="notifications-outline"
          title="Push notifications"
          hint="Get a banner on your phone even when the app is closed."
          value={prefs.pushEnabled}
          onValueChange={(v) => update.mutate({ pushEnabled: v })}
        />
      </Card>

      <SectionHeader title="Safety tips" />
      <Text variant="bodyMd" color={colors.textMuted}>
        How often would you like to receive safety tips?
      </Text>
      <View style={{ gap: spacing.md }}>
        {OPTIONS.map((o) => {
          const active = o.value === prefs.tipsFrequency;
          return (
            <Pressable key={o.value} onPress={() => update.mutate({ tipsFrequency: o.value })}>
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

function ChannelRow({
  icon,
  title,
  hint,
  value,
  onValueChange,
}: {
  icon: keyof typeof Ionicons.glyphMap;
  title: string;
  hint: string;
  value: boolean;
  onValueChange: (v: boolean) => void;
}) {
  return (
    <View style={{ flexDirection: 'row', alignItems: 'center', gap: spacing.md }}>
      <Ionicons name={icon} size={22} color={colors.primary} />
      <View style={{ flex: 1 }}>
        <Text variant="bodyMd" weight="semibold">
          {title}
        </Text>
        <Text variant="labelMd" color={colors.textMuted}>
          {hint}
        </Text>
      </View>
      <Switch
        value={value}
        onValueChange={onValueChange}
        trackColor={{ true: colors.primary, false: colors.border }}
        thumbColor={colors.white}
      />
    </View>
  );
}
