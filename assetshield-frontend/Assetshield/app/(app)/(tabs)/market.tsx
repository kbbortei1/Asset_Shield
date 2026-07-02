import { Ionicons } from '@expo/vector-icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { router } from 'expo-router';
import { Alert, View } from 'react-native';
import { AgentInterest, marketplaceApi } from '@/lib/api';
import { useAuth } from '@/lib/auth/AuthProvider';
import { Button, Card, EmptyState, ErrorState, ListScreen, ListSkeleton, Screen, StatusBadge, Text } from '@/components/ui';
import { colors, spacing } from '@/theme';

export default function MarketTab() {
  const { user } = useAuth();
  const role = user?.role ?? 'OWNER';
  if (role === 'AGENT') return <AgentLeads />;
  if (role === 'ADMIN') return <AdminAgents />;
  return <OwnerConnections />;
}

/** Stitch: "Connections" — agents who've expressed interest in my properties. */
function OwnerConnections() {
  const qc = useQueryClient();
  const q = useQuery({ queryKey: ['agent-interests'], queryFn: () => marketplaceApi.myAgentInterests({ size: 50 }) });

  const respond = useMutation({
    mutationFn: ({ id, accept }: { id: string; accept: boolean }) => marketplaceApi.respondInterest(id, accept),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['agent-interests'] }),
  });
  const revoke = useMutation({
    mutationFn: (id: string) => marketplaceApi.revokeInterest(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['agent-interests'] }),
  });

  if (q.isLoading) return (<Screen><ListSkeleton /></Screen>);
  if (q.isError) return <ErrorState onRetry={() => q.refetch()} />;
  const items = q.data?.items ?? [];

  return (
    <ListScreen
      data={items}
      keyExtractor={(it) => it.interestId}
      refreshing={q.isRefetching}
      onRefresh={q.refetch}
      header={
        <View style={{ gap: spacing.sm }}>
          <Text variant="headlineLgMobile">Connections</Text>
          <Text variant="bodyMd" color={colors.textMuted}>
            Agents interested in your properties. Accept to allow them to request a dossier.
          </Text>
        </View>
      }
      renderItem={({ item: it }) => (
        <ConnectionCard
          interest={it}
          onAccept={() => respond.mutate({ id: it.interestId, accept: true })}
          onDecline={() => respond.mutate({ id: it.interestId, accept: false })}
          onRevoke={() =>
            Alert.alert('Revoke access?', 'This immediately seals the agent’s access to this property.', [
              { text: 'Cancel', style: 'cancel' },
              { text: 'Revoke', style: 'destructive', onPress: () => revoke.mutate(it.interestId) },
            ])
          }
          busy={respond.isPending || revoke.isPending}
        />
      )}
      empty={<EmptyState icon="people-outline" title="No connections yet" body="When an agent expresses interest in a property you've opened to offers, it shows up here." />}
    />
  );
}

function ConnectionCard({
  interest,
  onAccept,
  onDecline,
  onRevoke,
  busy,
}: {
  interest: AgentInterest;
  onAccept: () => void;
  onDecline: () => void;
  onRevoke: () => void;
  busy: boolean;
}) {
  const pending = interest.status === 'PENDING';
  const accepted = interest.status === 'ACCEPTED';
  return (
    <Card>
      <View style={{ gap: spacing.sm }}>
        <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-start' }}>
          <View style={{ flex: 1 }}>
            <Text variant="headlineSm">{interest.agentName ?? 'Insurance agent'}</Text>
            <Text variant="labelMd" color={colors.textMuted}>
              {interest.insurerName ?? '—'} · {interest.propertyName ?? 'Property'}
            </Text>
          </View>
          <StatusBadge status={accepted ? 'secured' : pending ? 'needsUpdate' : 'damaged'} label={interest.status} />
        </View>
        {pending ? (
          <View style={{ flexDirection: 'row', gap: spacing.md }}>
            <Button title="Decline" variant="secondary" onPress={onDecline} loading={busy} />
            <Button title="Accept" onPress={onAccept} loading={busy} />
          </View>
        ) : accepted ? (
          <Button title="Revoke access" variant="danger" onPress={onRevoke} loading={busy} />
        ) : null}
      </View>
    </Card>
  );
}

/** Stitch: "Leads Marketplace" (agent). Leads are exactly 5 fields. */
function AgentLeads() {
  const me = useQuery({ queryKey: ['agent', 'me'], queryFn: () => marketplaceApi.agentMe() });
  const verified = (me.data?.verificationStatus ?? me.data?.status) === 'VERIFIED';
  const subscribed = me.data?.subscription?.status === 'ACTIVE';

  const q = useQuery({
    queryKey: ['leads'],
    queryFn: () => marketplaceApi.leads({ size: 50 }),
    enabled: verified && subscribed,
  });

  if (me.isLoading) return (<Screen><ListSkeleton /></Screen>);

  if (!verified || !subscribed) {
    return (
      <Screen>
        <Text variant="headlineLgMobile">Leads</Text>
        <Card style={{ backgroundColor: colors.tealTint }}>
          <Text variant="headlineSm" color={colors.primary}>
            {!verified ? 'Verification required' : 'Subscription required'}
          </Text>
          <Text variant="bodyMd" color={colors.primary}>
            {!verified
              ? 'An admin must verify your agent account before you can browse leads.'
              : 'Subscribe to unlock owner leads in your area.'}
          </Text>
        </Card>
        {verified && !subscribed ? (
          <Button title="Subscribe" onPress={() => router.push('/(app)/subscription' as never)} />
        ) : null}
      </Screen>
    );
  }

  if (q.isLoading) return (<Screen><ListSkeleton /></Screen>);
  if (q.isError) return <ErrorState onRetry={() => q.refetch()} />;
  const items = q.data?.items ?? [];

  return (
    <ListScreen
      data={items}
      keyExtractor={(lead) => lead.propertyId}
      refreshing={q.isRefetching}
      onRefresh={q.refetch}
      header={
        <View style={{ gap: spacing.sm }}>
          <Text variant="headlineLgMobile">Leads</Text>
          <Text variant="bodyMd" color={colors.textMuted}>
            Owners open to offers. Express interest to start a connection.
          </Text>
        </View>
      }
      renderItem={({ item: lead }) => (
        <Card onPress={() => router.push(`/(app)/agent/lead/${lead.propertyId}` as never)}>
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: spacing.md }}>
            <Ionicons name={lead.propertyType === 'COMMERCIAL' ? 'storefront' : 'home'} size={24} color={colors.primary} />
            <View style={{ flex: 1 }}>
              <Text variant="headlineSm">{lead.propertyName}</Text>
              <Text variant="labelMd" color={colors.textMuted}>
                {lead.locality} · {lead.ownerDisplayName}
              </Text>
            </View>
            <Ionicons name="chevron-forward" size={18} color={colors.textMuted} />
          </View>
        </Card>
      )}
      empty={<EmptyState icon="pricetags-outline" title="No leads right now" body="Check back later as more owners opt in." />}
    />
  );
}
// (admin queue below)

/** Admin: agent verification queue (MISSING_DESIGN, on-brand). */
function AdminAgents() {
  const q = useQuery({
    queryKey: ['admin', 'agents', 'PENDING_VERIFICATION'],
    queryFn: () => marketplaceApi.adminAgents('PENDING_VERIFICATION', { size: 50 }),
  });

  if (q.isLoading) return (<Screen><ListSkeleton /></Screen>);
  if (q.isError) return <ErrorState onRetry={() => q.refetch()} />;
  const items = q.data?.items ?? [];

  return (
    <ListScreen
      data={items}
      keyExtractor={(a, i) => a.agentId ?? a.id ?? String(i)}
      refreshing={q.isRefetching}
      onRefresh={q.refetch}
      header={
        <View style={{ gap: spacing.sm }}>
          <Text variant="headlineLgMobile">Agent queue</Text>
          <Text variant="bodyMd" color={colors.textMuted}>
            Review NIC licences and approve or reject agent accounts.
          </Text>
        </View>
      }
      renderItem={({ item: a }) => {
        const aid = a.agentId ?? a.id ?? '';
        return (
          <Card onPress={() => router.push(`/(app)/admin/agent/${aid}` as never)}>
            <View style={{ flexDirection: 'row', alignItems: 'center', gap: spacing.md }}>
              <Ionicons name="person-circle" size={32} color={colors.primary} />
              <View style={{ flex: 1 }}>
                <Text variant="headlineSm">{a.fullName ?? 'Agent'}</Text>
                <Text variant="labelMd" color={colors.textMuted}>
                  {a.insurerName ?? '—'} · {a.nicLicenceNo ?? 'No licence #'}
                </Text>
              </View>
              <Ionicons name="chevron-forward" size={18} color={colors.textMuted} />
            </View>
          </Card>
        );
      }}
      empty={<EmptyState icon="shield-checkmark-outline" title="Queue is clear" body="No agents awaiting verification." />}
    />
  );
}
