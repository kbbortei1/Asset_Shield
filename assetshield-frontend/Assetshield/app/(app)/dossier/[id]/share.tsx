import { Ionicons } from '@expo/vector-icons';
import { useMutation, useQuery } from '@tanstack/react-query';
import { useLocalSearchParams } from 'expo-router';
import { useState } from 'react';
import { Alert, View } from 'react-native';
import { isApiError, marketplaceApi } from '@/lib/api';
import { Button, Card, EmptyState, ErrorState, Header, Loading, Screen, Text } from '@/components/ui';
import { colors, spacing } from '@/theme';

/** CONSENT (beat 4): share a ready dossier with an accepted agent connection. */
export default function ShareDossier() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const dossierId = id!;
  const q = useQuery({ queryKey: ['agent-interests'], queryFn: () => marketplaceApi.myAgentInterests({ size: 50 }) });
  const [shared, setShared] = useState<Record<string, boolean>>({});
  const [busy, setBusy] = useState<string | null>(null);

  const share = useMutation({
    mutationFn: (interestId: string) => marketplaceApi.shareToAgent(dossierId, interestId),
  });

  const accepted = (q.data?.items ?? []).filter((i) => i.status === 'ACCEPTED');

  const onShare = async (interestId: string) => {
    setBusy(interestId);
    try {
      await share.mutateAsync(interestId);
      setShared((s) => ({ ...s, [interestId]: true }));
    } catch (e) {
      if (isApiError(e) && e.code === 'ALREADY_SHARED') setShared((s) => ({ ...s, [interestId]: true }));
      else Alert.alert('Could not share', isApiError(e) ? e.message : 'Try again.');
    } finally {
      setBusy(null);
    }
  };

  return (
    <Screen refreshing={q.isRefetching} onRefresh={q.refetch}>
      <Header title="Share dossier" />
      <Text variant="bodyMd" color={colors.textMuted}>
        Choose an agent you've connected with. Only accepted connections can receive a dossier, and you can revoke access anytime.
      </Text>

      {q.isLoading ? (
        <Loading />
      ) : q.isError ? (
        <ErrorState onRetry={() => q.refetch()} />
      ) : accepted.length === 0 ? (
        <EmptyState
          icon="people-outline"
          title="No connections yet"
          body="Accept an agent's interest from Connections before you can share a dossier."
        />
      ) : (
        accepted.map((i) => (
          <Card key={i.interestId}>
            <View style={{ flexDirection: 'row', alignItems: 'center', gap: spacing.md }}>
              <Ionicons name="person-circle" size={32} color={colors.primary} />
              <View style={{ flex: 1 }}>
                <Text variant="bodyMd" weight="semibold">
                  {i.agentName ?? 'Agent'}
                </Text>
                <Text variant="labelMd" color={colors.textMuted}>
                  {i.insurerName ?? '-'} · {i.propertyName ?? ''}
                </Text>
              </View>
              {shared[i.interestId] ? (
                <View style={{ flexDirection: 'row', alignItems: 'center', gap: 4 }}>
                  <Ionicons name="checkmark-circle" size={20} color={colors.success} />
                  <Text variant="labelMd" color={colors.success}>
                    Shared
                  </Text>
                </View>
              ) : (
                <Button title="Share" fullWidth={false} loading={busy === i.interestId} onPress={() => onShare(i.interestId)} />
              )}
            </View>
          </Card>
        ))
      )}
    </Screen>
  );
}
