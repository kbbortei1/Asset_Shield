import { Ionicons } from '@expo/vector-icons';
import { useQuery } from '@tanstack/react-query';
import { View } from 'react-native';
import { AuditEvent, usersApi } from '@/lib/api';
import { Card, EmptyState, ErrorState, Header, ListScreen, Loading, Text } from '@/components/ui';
import { colors, spacing } from '@/theme';

const ACTION_ICONS: Record<string, keyof typeof Ionicons.glyphMap> = {
  LOGIN_SUCCESS: 'log-in-outline',
  LOGIN_FAILED: 'warning-outline',
  ACCOUNT_VERIFIED: 'checkmark-circle-outline',
  PASSWORD_RESET: 'key-outline',
  ADMIN_CREATED: 'person-add-outline',
  ACCOUNT_PURGED: 'trash-outline',
};

function titleCase(v: string): string {
  return v.split('_').map((w) => w[0] + w.slice(1).toLowerCase()).join(' ');
}

/** Admin-only security audit trail (auth events, newest first). */
export default function AuditTrail() {
  const q = useQuery({
    queryKey: ['audit-events'],
    queryFn: () => usersApi.auditEvents({ size: 100 }),
  });

  if (q.isLoading) return <Loading />;
  if (q.isError) return <ErrorState onRetry={() => q.refetch()} />;
  const items = q.data?.items ?? [];

  return (
    <ListScreen
      data={items}
      keyExtractor={(e) => e.id}
      refreshing={q.isRefetching}
      onRefresh={() => q.refetch()}
      header={<Header title="Audit trail" />}
      renderItem={({ item }) => <AuditRow event={item} />}
      empty={
        <EmptyState
          icon="shield-outline"
          title="No events yet"
          body="Logins, verifications and admin actions will appear here."
        />
      }
    />
  );
}

function AuditRow({ event }: { event: AuditEvent }) {
  const failed = event.action === 'LOGIN_FAILED';
  return (
    <Card>
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: spacing.md }}>
        <Ionicons
          name={ACTION_ICONS[event.action] ?? 'ellipse-outline'}
          size={20}
          color={failed ? colors.error : colors.primary}
        />
        <View style={{ flex: 1 }}>
          <Text variant="bodyMd" weight="semibold" color={failed ? colors.error : undefined}>
            {titleCase(event.action)}
          </Text>
          <Text variant="labelMd" color={colors.textMuted} numberOfLines={1}>
            {[event.target, event.detail].filter(Boolean).join(' · ') || '—'}
          </Text>
        </View>
        <Text variant="labelMd" color={colors.textMuted}>
          {new Date(event.createdAt).toLocaleString()}
        </Text>
      </View>
    </Card>
  );
}
