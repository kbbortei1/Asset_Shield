import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { notificationsApi } from '@/lib/api';
import { TipCard } from '@/components/cards/TipCard';
import { EmptyState, ErrorState, Header, ListScreen, Loading, Screen } from '@/components/ui';

/** Global safety tips feed (English-only by design). Grows daily — virtualized. */
export default function TipsFeed() {
  const qc = useQueryClient();
  const q = useQuery({ queryKey: ['tips-feed'], queryFn: () => notificationsApi.feed({ size: 50 }) });
  const markRead = useMutation({
    mutationFn: (tipId: string) => notificationsApi.markTipRead(tipId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['tips-feed'] }),
  });

  if (q.isLoading)
    return (
      <Screen>
        <Header title="Safety tips" />
        <Loading />
      </Screen>
    );
  if (q.isError) return <ErrorState onRetry={() => q.refetch()} />;

  return (
    <ListScreen
      data={q.data?.items ?? []}
      keyExtractor={(t) => t.id}
      refreshing={q.isRefetching}
      onRefresh={q.refetch}
      header={<Header title="Safety tips" />}
      renderItem={({ item: t }) => <TipCard tip={t} onPress={() => !t.readAt && markRead.mutate(t.id)} />}
      empty={<EmptyState icon="bulb-outline" title="No tips yet" body="Safety tips will appear here." />}
    />
  );
}
