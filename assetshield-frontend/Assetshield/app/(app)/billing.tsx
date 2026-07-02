import { Ionicons } from '@expo/vector-icons';
import { useQuery } from '@tanstack/react-query';
import { View } from 'react-native';
import { damageApi, marketplaceApi } from '@/lib/api';
import { useAuth } from '@/lib/auth/AuthProvider';
import { Card, EmptyState, Header, Loading, Screen, StatusBadge, Text } from '@/components/ui';
import { colors, spacing } from '@/theme';

/**
 * Stitch: "Billing History". The backend exposes no list-payments endpoint, so
 * this aggregates the billable artifacts we CAN read: the current subscription
 * plus each generated dossier (the dossier fee). Payment-by-reference details
 * are available via GET /payments/{reference} when a reference is known.
 */
export default function Billing() {
  const { user } = useAuth();
  const isAgent = user?.role === 'AGENT';

  const sub = useQuery({
    queryKey: ['subscription', isAgent ? 'agent' : 'owner'],
    queryFn: () => (isAgent ? marketplaceApi.agentSubscription() : marketplaceApi.mySubscription()),
  });
  const dossiers = useQuery({
    queryKey: ['my-dossiers'],
    queryFn: () => damageApi.myDossiers({ size: 50 }),
    enabled: !isAgent,
  });

  return (
    <Screen refreshing={sub.isRefetching} onRefresh={() => { sub.refetch(); dossiers.refetch(); }}>
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

      {!isAgent ? (
        <>
          <Text variant="headlineSm" style={{ marginTop: spacing.md }}>
            Dossier fees
          </Text>
          {dossiers.isLoading ? (
            <Loading />
          ) : (dossiers.data?.items.length ?? 0) === 0 ? (
            <EmptyState icon="receipt-outline" title="No charges yet" body="Dossier generation fees will appear here." />
          ) : (
            dossiers.data!.items.map((d) => (
              <Card key={d.id}>
                <View style={{ flexDirection: 'row', alignItems: 'center', gap: spacing.md }}>
                  <Ionicons name="document-text" size={22} color={colors.primary} />
                  <View style={{ flex: 1 }}>
                    <Text variant="bodyMd" weight="semibold">
                      {d.propertyName ?? 'Dossier'}
                    </Text>
                    <Text variant="labelMd" color={colors.textMuted}>
                      {d.generatedAt ? new Date(d.generatedAt).toLocaleDateString() : d.disasterType ?? 'Dossier fee'}
                    </Text>
                  </View>
                  <StatusBadge
                    status={d.status === 'READY' ? 'secured' : d.status === 'FAILED' ? 'damaged' : 'needsUpdate'}
                    label={d.status === 'PENDING_PAYMENT' ? 'Unpaid' : 'Paid'}
                  />
                </View>
              </Card>
            ))
          )}
        </>
      ) : null}
    </Screen>
  );
}
