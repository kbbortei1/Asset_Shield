import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useLocalSearchParams } from 'expo-router';
import { notificationsApi } from '@/lib/api';
import { TipCard } from '@/components/cards/TipCard';
import { EmptyState, ErrorState, Header, Loading, Screen } from '@/components/ui';

/** Property-specific safety tips. Tips are English-only by design. */
export default function PropertyTips() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const propertyId = id!;
  const qc = useQueryClient();
  const q = useQuery({ queryKey: ['tips', propertyId], queryFn: () => notificationsApi.propertyTips(propertyId, { size: 50 }) });
  const markRead = useMutation({
    mutationFn: (tipId: string) => notificationsApi.markTipRead(tipId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['tips', propertyId] }),
  });

  return (
    <Screen refreshing={q.isRefetching} onRefresh={q.refetch}>
      <Header title="Safety tips" />
      {q.isLoading ? (
        <Loading />
      ) : q.isError ? (
        <ErrorState onRetry={() => q.refetch()} />
      ) : (q.data?.items.length ?? 0) === 0 ? (
        <EmptyState icon="bulb-outline" title="No tips yet" body="Personalised safety tips for this property will appear here." />
      ) : (
        q.data!.items.map((t) => <TipCard key={t.id} tip={t} onPress={() => !t.readAt && markRead.mutate(t.id)} />)
      )}
    </Screen>
  );
}
