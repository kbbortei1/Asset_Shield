import { Ionicons } from '@expo/vector-icons';
import { useQuery } from '@tanstack/react-query';
import { router, useLocalSearchParams } from 'expo-router';
import { useState } from 'react';
import { View } from 'react-native';
import { damageApi, isApiError, marketplaceApi } from '@/lib/api';
import { Button, Card, EmptyState, ErrorState, Header, ListScreen, Loading, Text, formatCedis, useToast, showAlert } from '@/components/ui';
import { colors, spacing } from '@/theme';

/**
 * CONSENT (beat 4), reverse entry: reached from an accepted connection so the
 * owner can pick a ready dossier to share with THIS agent. The dossier→agent
 * direction lives in dossier/[id]/share; this is agent→dossier.
 */
export default function ShareWithConnection() {
  const { interestId, agent } = useLocalSearchParams<{ interestId: string; agent?: string }>();
  const { show } = useToast();
  const [shared, setShared] = useState<Record<string, boolean>>({});
  const [busy, setBusy] = useState<string | null>(null);

  const q = useQuery({ queryKey: ['my-dossiers'], queryFn: () => damageApi.myDossiers({ size: 50 }) });
  // only a generated (READY) dossier can be shared for verification + a quote
  const ready = (q.data?.items ?? []).filter((d) => d.status === 'READY');

  const onShare = async (dossierId: string) => {
    setBusy(dossierId);
    try {
      await marketplaceApi.shareToAgent(dossierId, interestId!);
      setShared((s) => ({ ...s, [dossierId]: true }));
      show('Dossier shared with the agent');
    } catch (e) {
      if (isApiError(e) && e.code === 'ALREADY_SHARED') {
        setShared((s) => ({ ...s, [dossierId]: true }));
        show('Already shared with this agent');
      } else {
        showAlert('Could not share', isApiError(e) ? e.message : 'Try again.');
      }
    } finally {
      setBusy(null);
    }
  };

  const header = (
    <View style={{ gap: spacing.md }}>
      <Header title="Share a dossier" />
      <Card style={{ backgroundColor: colors.tealTint }}>
        <View style={{ flexDirection: 'row', gap: spacing.md, alignItems: 'flex-start' }}>
          <Ionicons name="share-social" size={22} color={colors.primary} />
          <View style={{ flex: 1, gap: 2 }}>
            <Text variant="bodyMd" weight="semibold" color={colors.primary}>
              Next step with {agent || 'this agent'}
            </Text>
            <Text variant="labelMd" color={colors.primary}>
              Share a damage dossier so they can verify your evidence and send you an insurance
              quote. You can revoke their access anytime from Connections.
            </Text>
          </View>
        </View>
      </Card>
    </View>
  );

  if (q.isLoading) return <Loading />;
  if (q.isError) return <ErrorState onRetry={() => q.refetch()} />;

  return (
    <ListScreen
      data={ready}
      keyExtractor={(d) => d.id}
      refreshing={q.isRefetching}
      onRefresh={q.refetch}
      header={header}
      renderItem={({ item: d }) => (
        <Card>
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: spacing.md }}>
            <Ionicons name="folder-open" size={26} color={colors.primary} />
            <View style={{ flex: 1 }}>
              <Text variant="bodyMd" weight="semibold">
                {d.propertyName ?? 'Dossier'}
              </Text>
              <Text variant="labelMd" color={colors.textMuted}>
                {d.disasterType ?? 'Damage dossier'}
                {typeof d.totalEstimatedLoss === 'number' ? ` · ${formatCedis(d.totalEstimatedLoss)}` : ''}
              </Text>
            </View>
            {shared[d.id] ? (
              <View style={{ flexDirection: 'row', alignItems: 'center', gap: 4 }}>
                <Ionicons name="checkmark-circle" size={20} color={colors.success} />
                <Text variant="labelMd" color={colors.success}>
                  Shared
                </Text>
              </View>
            ) : (
              <Button title="Share" fullWidth={false} loading={busy === d.id} onPress={() => onShare(d.id)} />
            )}
          </View>
        </Card>
      )}
      empty={
        <EmptyState
          icon="document-text-outline"
          title="No ready dossiers"
          body="Complete a damage report and generate its dossier first, then share it here to get a quote."
          actionLabel="Go to Dossiers"
          onAction={() => router.replace('/(app)/(tabs)/activity' as never)}
        />
      }
    />
  );
}
