import { Ionicons } from '@expo/vector-icons';
import { useQuery } from '@tanstack/react-query';
import { View } from 'react-native';
import { marketplaceApi, Payment } from '@/lib/api';
import { useAuth } from '@/lib/auth/AuthProvider';
import { Card, EmptyState, Header, ListScreen, Loading, Screen, StatusBadge, Text, formatCedis } from '@/components/ui';
import { colors, spacing } from '@/theme';

const PURPOSE_META: Record<string, { icon: keyof typeof Ionicons.glyphMap; label: string }> = {
  PRO_SUBSCRIPTION: { icon: 'star', label: 'AssetShield PRO' },
  AGENT_SUBSCRIPTION: { icon: 'star', label: 'Agent subscription' },
  DOSSIER_FEE: { icon: 'document-text', label: 'Dossier fee' },
};

/** Stitch: "Billing History" — real payment records from payment-service. */
export default function Billing() {
  const { user } = useAuth();
  const isAgent = user?.role === 'AGENT';

  const sub = useQuery({
    queryKey: ['subscription', isAgent ? 'agent' : 'owner'],
    queryFn: () => (isAgent ? marketplaceApi.agentSubscription() : marketplaceApi.mySubscription()),
  });
  const payments = useQuery({
    queryKey: ['my-payments'],
    queryFn: () => marketplaceApi.myPayments({ size: 50 }),
  });

  const header = (
    <View style={{ gap: spacing.lg }}>
      <Header title="Billing history" />

      <Text variant="headlineSm">Subscription</Text>
      {sub.isLoading ? (
        <Loading />
      ) : (
        <Card>
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: spacing.md }}>
            <Ionicons name="star" size={24} color={colors.cta} />
            <View style={{ flex: 1 }}>
              <Text variant="bodyMd" weight="semibold">
                {isAgent ? 'Agent subscription' : 'AssetShield PRO'}
              </Text>
              <Text variant="labelMd" color={colors.textMuted}>
                {sub.data?.expiresAt ? `Renews ${new Date(sub.data.expiresAt).toLocaleDateString()}` : 'No active renewal'}
              </Text>
            </View>
            <StatusBadge status={sub.data?.status === 'ACTIVE' ? 'secured' : 'needsUpdate'} label={sub.data?.status ?? 'INACTIVE'} />
          </View>
        </Card>
      )}

      <Text variant="headlineSm">Payments</Text>
    </View>
  );

  if (payments.isLoading)
    return (
      <Screen>
        {header}
        <Loading />
      </Screen>
    );

  return (
    <ListScreen
      data={payments.data?.items ?? []}
      keyExtractor={(p) => p.reference}
      refreshing={payments.isRefetching}
      onRefresh={() => {
        sub.refetch();
        payments.refetch();
      }}
      header={header}
      renderItem={({ item }) => <PaymentRow p={item} />}
      empty={<EmptyState icon="receipt-outline" title="No charges yet" body="Subscription and dossier payments will appear here." />}
    />
  );
}

function PaymentRow({ p }: { p: Payment }) {
  const meta = PURPOSE_META[p.purpose ?? ''] ?? { icon: 'card' as const, label: p.purpose ?? 'Payment' };
  return (
    <Card>
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: spacing.md }}>
        <Ionicons name={meta.icon} size={22} color={colors.primary} />
        <View style={{ flex: 1 }}>
          <Text variant="bodyMd" weight="semibold">
            {meta.label}
          </Text>
          <Text variant="labelMd" color={colors.textMuted}>
            {p.createdAt ? new Date(p.createdAt).toLocaleDateString() : p.reference}
          </Text>
        </View>
        <View style={{ alignItems: 'flex-end', gap: 4 }}>
          {typeof p.amount === 'number' ? (
            <Text variant="bodyMd" weight="semibold">
              {formatCedis(p.amount)}
            </Text>
          ) : null}
          <StatusBadge
            status={p.status === 'SUCCESS' ? 'secured' : p.status === 'FAILED' ? 'damaged' : 'needsUpdate'}
            label={p.status === 'INITIATED' ? 'UNPAID' : p.status}
          />
        </View>
      </View>
    </Card>
  );
}
