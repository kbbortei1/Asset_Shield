import { Ionicons } from '@expo/vector-icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { router } from 'expo-router';
import { View } from 'react-native';
import { damageApi, DossierStatus, marketplaceApi } from '@/lib/api';
import { useAuth } from '@/lib/auth/AuthProvider';
import {
  AnimatedItem,
  AssetStatus,
  Button,
  Card,
  EmptyState,
  ErrorState,
  GatedNotice,
  ListScreen,
  ListSkeleton,
  Screen,
  SectionHeader,
  StatusBadge,
  Text,
  formatCedis,
  useToast,
} from '@/components/ui';
import { colors, spacing } from '@/theme';

export default function ActivityTab() {
  const { user } = useAuth();
  return user?.role === 'AGENT' ? <AgentActivity /> : <OwnerActivity />;
}

function dossierBadge(status?: DossierStatus): { status: AssetStatus; label: string } {
  switch (status) {
    case 'READY':
      return { status: 'secured', label: 'Ready' };
    case 'FAILED':
      return { status: 'damaged', label: 'Failed' };
    case 'GENERATING':
      return { status: 'needsUpdate', label: 'Generating' };
    default:
      return { status: 'needsUpdate', label: 'Pending payment' };
  }
}

/** Owner: dossiers + quotes received. */
function OwnerActivity() {
  const qc = useQueryClient();
  const { show } = useToast();
  const dossiers = useQuery({ queryKey: ['my-dossiers'], queryFn: () => damageApi.myDossiers({ size: 50 }) });
  const quotes = useQuery({ queryKey: ['my-quotes'], queryFn: () => marketplaceApi.myQuotes({ size: 50 }) });

  const respond = useMutation({
    mutationFn: ({ id, accept }: { id: string; accept: boolean }) => marketplaceApi.respondQuote(id, accept),
    onSuccess: (_d, v) => {
      qc.invalidateQueries({ queryKey: ['my-quotes'] });
      show(v.accept ? 'Quote accepted' : 'Quote declined');
    },
  });

  if (dossiers.isLoading)
    return (
      <Screen>
        <ListSkeleton />
      </Screen>
    );
  if (dossiers.isError) return <ErrorState onRetry={() => dossiers.refetch()} />;

  const dItems = dossiers.data?.items ?? [];
  const qItems = quotes.data?.items ?? [];

  const footer =
    qItems.length > 0 ? (
      <View style={{ gap: spacing.lg }}>
        <SectionHeader title="Quotes received" />
        {qItems.map((quote) => (
          <Card key={quote.quoteId}>
            <View style={{ gap: spacing.sm }}>
              <View style={{ flexDirection: 'row', justifyContent: 'space-between' }}>
                <Text variant="headlineSm">{quote.insurerName ?? quote.agentName ?? 'Quote'}</Text>
                <StatusBadge
                  status={quote.status === 'ACCEPTED' ? 'secured' : quote.status === 'DECLINED' ? 'damaged' : 'needsUpdate'}
                  label={quote.status}
                />
              </View>
              <Text variant="bodyMd" color={colors.textMuted}>
                Cover {formatCedis(quote.coverageAmount)} · Premium {formatCedis(quote.premium)}/mo · {quote.termMonths} months
              </Text>
              {quote.status === 'PENDING' ? (
                <View style={{ flexDirection: 'row', gap: spacing.md }}>
                  <Button title="Decline" variant="secondary" loading={respond.isPending} onPress={() => respond.mutate({ id: quote.quoteId, accept: false })} />
                  <Button title="Accept" loading={respond.isPending} onPress={() => respond.mutate({ id: quote.quoteId, accept: true })} />
                </View>
              ) : null}
            </View>
          </Card>
        ))}
      </View>
    ) : null;

  return (
    <ListScreen
      data={dItems}
      keyExtractor={(d) => d.id}
      refreshing={dossiers.isRefetching}
      onRefresh={() => { dossiers.refetch(); quotes.refetch(); }}
      header={<Text variant="headlineLgMobile">Dossiers</Text>}
      footer={footer}
      renderItem={({ item: d, index }) => {
        const b = dossierBadge(d.status);
        return (
          <AnimatedItem index={index}>
            <Card onPress={() => router.push(`/(app)/dossier/${d.id}` as never)}>
              <View style={{ flexDirection: 'row', alignItems: 'center', gap: spacing.md }}>
                <Ionicons name="folder-open" size={26} color={colors.primary} />
                <View style={{ flex: 1 }}>
                  <Text variant="headlineSm">{d.propertyName ?? 'Dossier'}</Text>
                  <Text variant="labelMd" color={colors.textMuted}>
                    {d.disasterType ?? 'Damage dossier'}
                  </Text>
                </View>
                <StatusBadge status={b.status} label={b.label} />
              </View>
            </Card>
          </AnimatedItem>
        );
      }}
      empty={<EmptyState icon="document-text-outline" title="No dossiers yet" body="Complete a damage report and generate a dossier to see it here." />}
    />
  );
}

/** Agent: dossiers shared with me + quotes I've issued. */
function AgentActivity() {
  // Shared dossiers are gated on an active subscription server-side, so check
  // the agent's status first and show a calm "subscribe" wall instead of the
  // generic error screen an unsubscribed agent used to hit here.
  const me = useQuery({ queryKey: ['agent', 'me'], queryFn: () => marketplaceApi.agentMe() });
  const verified = (me.data?.verificationStatus ?? me.data?.status) === 'VERIFIED';
  const subscribed = me.data?.subscription?.status === 'ACTIVE';
  const unlocked = verified && subscribed;

  const shared = useQuery({
    queryKey: ['shared-dossiers'],
    queryFn: () => marketplaceApi.sharedDossiers({ size: 50 }),
    enabled: unlocked,
  });
  const quotes = useQuery({
    queryKey: ['agent-quotes'],
    queryFn: () => marketplaceApi.myQuotes({ size: 50 }),
    enabled: unlocked,
  });

  if (me.isLoading || (unlocked && shared.isLoading))
    return (
      <Screen>
        <ListSkeleton />
      </Screen>
    );

  if (!unlocked) {
    return (
      <Screen>
        <Text variant="headlineLgMobile" style={{ marginBottom: spacing.lg }}>
          Shared dossiers
        </Text>
        <GatedNotice
          icon={verified ? 'lock-closed' : 'shield-checkmark-outline'}
          title={verified ? 'Subscription required' : 'Verification required'}
          body={
            verified
              ? 'Subscribe to receive and verify dossiers that owners share with you, then send quotes.'
              : 'An admin must verify your agent account before owners can share dossiers with you.'
          }
          actionLabel={verified ? 'Subscribe' : undefined}
          onAction={verified ? () => router.push('/(app)/subscription' as never) : undefined}
        />
      </Screen>
    );
  }

  if (shared.isError) return <ErrorState onRetry={() => shared.refetch()} />;
  const items = shared.data?.items ?? [];
  const qItems = quotes.data?.items ?? [];

  const footer =
    qItems.length > 0 ? (
      <View style={{ gap: spacing.lg }}>
        <SectionHeader title="Quotes I've issued" />
        {qItems.map((quote) => (
          <Card key={quote.quoteId}>
            <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
              <View style={{ flex: 1 }}>
                <Text variant="headlineSm">{quote.propertyName ?? 'Quote'}</Text>
                <Text variant="labelMd" color={colors.textMuted}>
                  Cover {formatCedis(quote.coverageAmount)} · {quote.termMonths} mo
                </Text>
              </View>
              <StatusBadge
                status={quote.status === 'ACCEPTED' ? 'secured' : quote.status === 'DECLINED' ? 'damaged' : 'needsUpdate'}
                label={quote.status}
              />
            </View>
          </Card>
        ))}
      </View>
    ) : null;

  return (
    <ListScreen
      data={items}
      keyExtractor={(s) => s.dossierId}
      refreshing={shared.isRefetching}
      onRefresh={() => { shared.refetch(); quotes.refetch(); }}
      header={<Text variant="headlineLgMobile">Shared dossiers</Text>}
      footer={footer}
      renderItem={({ item: s }) => (
        <Card onPress={() => router.push(`/(app)/agent/dossier/${s.dossierId}` as never)}>
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: spacing.md }}>
            <Ionicons name="folder-open" size={26} color={colors.primary} />
            <View style={{ flex: 1 }}>
              <Text variant="headlineSm">{s.propertyName ?? 'Dossier'}</Text>
              <Text variant="labelMd" color={colors.textMuted}>
                {s.ownerName ?? 'Owner'}
              </Text>
            </View>
            <Ionicons name="chevron-forward" size={18} color={colors.textMuted} />
          </View>
        </Card>
      )}
      empty={<EmptyState icon="share-social-outline" title="Nothing shared yet" body="When an owner shares a dossier with you, it appears here for verification and quoting." />}
    />
  );
}
