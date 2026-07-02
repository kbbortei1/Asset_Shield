import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { notificationsApi } from '@/lib/api';
import { TipCard } from '@/components/cards/TipCard';
import { EmptyState, ErrorState, Header, Loading, Screen } from '@/components/ui';

/** Global safety tips feed (English-only by design). */
export default function TipsFeed() {
  const qc = useQueryClient();
  const q = useQuery({ queryKey: ['tips-feed'], queryFn: () => notificationsApi.feed({ size: 50 }) });
  const markRead = useMutation({
    mutationFn: (tipId: string) => notificationsApi.markTipRead(tipId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['tips-feed'] }),
  });

  return (
    <Screen refreshing={q.isRefetching} onRefresh={q.refetch}>
      <Header title="Safety tips" />
      {q.isLoading ? (
        <Loading />
      ) : q.isError ? (
        <ErrorState onRetry={() => q.refetch()} />
      ) : (q.data?.items.length ?? 0) === 0 ? (
        <EmptyState icon="bulb-outline" title="No tips yet" body="Safety tips will appear here." />
      ) : (
        q.data!.items.map((t) => <TipCard key={t.id} tip={t} onPress={() => !t.readAt && markRead.mutate(t.id)} />)
      )}
    </Screen>
  );
}
