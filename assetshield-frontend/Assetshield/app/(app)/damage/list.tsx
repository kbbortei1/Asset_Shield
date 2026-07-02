import { Ionicons } from '@expo/vector-icons';
import { useQuery } from '@tanstack/react-query';
import { router, useLocalSearchParams } from 'expo-router';
import { View } from 'react-native';
import { damageApi } from '@/lib/api';
import { Button, Card, EmptyState, ErrorState, Header, ListScreen, Loading, Screen, StatusBadge, Text } from '@/components/ui';
import { colors, spacing } from '@/theme';

export default function DamageReportList() {
  const { propertyId } = useLocalSearchParams<{ propertyId: string }>();
  const q = useQuery({
    queryKey: ['reports', propertyId],
    queryFn: () => damageApi.listByProperty(propertyId!, { size: 50 }),
    enabled: !!propertyId,
  });

  const header = (
    <View style={{ gap: spacing.lg }}>
      <Header title="Damage reports" />
      <Button title="New report" onPress={() => router.push(`/(app)/damage/new?propertyId=${propertyId}` as never)} />
    </View>
  );

  if (q.isLoading)
    return (
      <Screen>
        {header}
        <Loading />
      </Screen>
    );
  if (q.isError) return <ErrorState onRetry={() => q.refetch()} />;

  return (
    <ListScreen
      data={q.data?.items ?? []}
      keyExtractor={(r) => r.id}
      refreshing={q.isRefetching}
      onRefresh={q.refetch}
      header={header}
      renderItem={({ item: r }) => (
        <Card onPress={() => router.push(`/(app)/damage/${r.id}` as never)}>
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: spacing.md }}>
            <Ionicons name="document-attach" size={26} color={colors.primary} />
            <View style={{ flex: 1 }}>
              <Text variant="headlineSm">{r.disasterType}</Text>
              <Text variant="labelMd" color={colors.textMuted}>
                {r.photoCount ?? 0} photos · {r.occurredAt ? new Date(r.occurredAt).toLocaleDateString() : ''}
              </Text>
            </View>
            <StatusBadge status={r.status === 'COMPLETED' ? 'secured' : 'needsUpdate'} label={r.status} />
          </View>
        </Card>
      )}
      empty={<EmptyState icon="alert-circle-outline" title="No reports" body="Open a report when damage occurs to build your claim dossier." />}
    />
  );
}
