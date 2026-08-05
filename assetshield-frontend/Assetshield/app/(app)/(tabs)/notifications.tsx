import { Ionicons } from '@expo/vector-icons';
import { useQuery } from '@tanstack/react-query';
import { router } from 'expo-router';
import { useEffect } from 'react';
import { View } from 'react-native';
import { AppNotification, notificationsApi } from '@/lib/api';
import { routeForNotification } from '@/lib/push/deeplink';
import { useMarkNotificationsSeen } from '@/lib/notifications/unread';
import { Card, EmptyState, ErrorState, ListScreen, ListSkeleton, Screen, Text } from '@/components/ui';
import { colors, spacing } from '@/theme';

const ICONS: Record<string, keyof typeof Ionicons.glyphMap> = {
  TIP: 'bulb',
  REDOC_REMINDER: 'refresh-circle',
  DOSSIER_READY: 'document-text',
  HOUSEHOLD_INVITE: 'people',
  AGENT_INTEREST: 'pricetag',
  INTEREST_RESPONSE: 'chatbubble-ellipses',
  INTEREST_REVOKED: 'close-circle',
  SHARE_CREATED: 'share-social',
  SHARE_REVOKED: 'lock-closed',
  QUOTE_ISSUED: 'cash',
  QUOTE_RESPONSE: 'cash',
  AGENT_VERIFIED: 'shield-checkmark',
  AGENT_REJECTED: 'shield',
  SUBSCRIPTION_EXPIRY: 'time',
  MAINTENANCE_DUE: 'construct',
  ANNOUNCEMENT: 'megaphone',
  CHAT_MESSAGE: 'chatbubble-ellipses',
};

export default function NotificationsTab() {
  const q = useQuery({
    queryKey: ['notifications'],
    queryFn: () => notificationsApi.list({ size: 50 }),
    refetchInterval: 20_000, // keep the open list live (new message alerts appear)
    refetchOnWindowFocus: true,
  });
  const markSeen = useMarkNotificationsSeen();

  // Opening this screen clears the bell badge: everything up to now is "seen".
  useEffect(() => {
    if (q.data) markSeen();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [q.data]);

  const header = (
    <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
      <Text variant="headlineLgMobile">Alerts</Text>
      <Ionicons
        name="bulb-outline"
        size={24}
        color={colors.primary}
        accessibilityRole="button"
        accessibilityLabel="Safety tips"
        onPress={() => router.push('/(app)/tips' as never)}
      />
    </View>
  );

  if (q.isLoading)
    return (
      <Screen>
        {header}
        <ListSkeleton />
      </Screen>
    );
  if (q.isError) return <ErrorState onRetry={() => q.refetch()} />;

  return (
    <ListScreen
      data={q.data?.items ?? []}
      keyExtractor={(n) => n.id}
      refreshing={q.isRefetching}
      onRefresh={q.refetch}
      header={header}
      renderItem={({ item }) => <NotificationRow n={item} />}
      empty={
        <EmptyState icon="notifications-outline" title="No alerts yet" body="Updates about your dossiers, connections and quotes will appear here." />
      }
    />
  );
}

function NotificationRow({ n }: { n: AppNotification }) {
  const icon = ICONS[n.type] ?? 'notifications';
  return (
    <Card onPress={() => router.push(routeForNotification(n.type, n.payload) as never)} padded>
      <View style={{ flexDirection: 'row', gap: spacing.md, alignItems: 'flex-start' }}>
        <View
          style={{
            width: 40,
            height: 40,
            borderRadius: 12,
            backgroundColor: colors.tealTint,
            alignItems: 'center',
            justifyContent: 'center',
          }}
        >
          <Ionicons name={icon} size={20} color={colors.primary} />
        </View>
        <View style={{ flex: 1, gap: 2 }}>
          <Text variant="bodyMd" weight="semibold">
            {n.title ?? n.type}
          </Text>
          {n.body ? (
            <Text variant="labelMd" color={colors.textMuted}>
              {n.body}
            </Text>
          ) : null}
          {n.createdAt ? (
            <Text variant="labelMd" color={colors.textMuted}>
              {new Date(n.createdAt).toLocaleDateString()}
            </Text>
          ) : null}
        </View>
      </View>
    </Card>
  );
}
