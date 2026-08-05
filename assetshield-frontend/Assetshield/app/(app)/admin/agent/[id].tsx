import { Ionicons } from '@expo/vector-icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { router, useLocalSearchParams } from 'expo-router';
import { useState } from 'react';
import { View } from 'react-native';
import { isApiError, marketplaceApi } from '@/lib/api';
import { Button, Card, ErrorState, Header, Input, Loading, Screen, Text, showAlert } from '@/components/ui';
import { colors, spacing } from '@/theme';

/** Admin: review an agent's NIC licence and approve or reject (MISSING_DESIGN). */
export default function AgentReview() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const agentId = id!;
  const qc = useQueryClient();
  const [rejecting, setRejecting] = useState(false);
  const [reason, setReason] = useState('');

  const q = useQuery({
    queryKey: ['admin', 'agents', 'PENDING_VERIFICATION'],
    queryFn: () => marketplaceApi.adminAgents('PENDING_VERIFICATION', { size: 50 }),
  });
  const agent = q.data?.items.find((a) => (a.agentId ?? a.id) === agentId);

  const decide = useMutation({
    mutationFn: ({ approve, rejectionReason }: { approve: boolean; rejectionReason?: string }) =>
      marketplaceApi.verifyAgent(agentId, approve, rejectionReason),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin', 'agents', 'PENDING_VERIFICATION'] });
      router.back();
    },
    onError: (e) => showAlert('Could not update', isApiError(e) ? e.message : 'Try again.'),
  });

  if (q.isLoading) return <Loading />;
  if (q.isError) return <ErrorState onRetry={() => q.refetch()} />;
  if (!agent) {
    return (
      <Screen>
        <Header title="Agent" />
        <Text variant="bodyMd" color={colors.textMuted}>
          This agent is no longer in the queue.
        </Text>
      </Screen>
    );
  }

  return (
    <Screen>
      <Header title="Review agent" />
      <Card>
        <View style={{ gap: spacing.sm }}>
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: spacing.md }}>
            <Ionicons name="person-circle" size={40} color={colors.primary} />
            <View style={{ flex: 1 }}>
              <Text variant="headlineSm">{agent.fullName ?? 'Agent'}</Text>
              <Text variant="labelMd" color={colors.textMuted}>
                {agent.insurerName ?? '-'}
              </Text>
            </View>
          </View>
          <Row label="NIC licence" value={agent.nicLicenceNo ?? '-'} />
          <Row label="Status" value={agent.verificationStatus ?? agent.status ?? 'PENDING_VERIFICATION'} />
        </View>
      </Card>

      {rejecting ? (
        <View style={{ gap: spacing.md }}>
          <Input label="Rejection reason" value={reason} onChangeText={setReason} placeholder="e.g. Licence could not be verified" multiline style={{ height: 100, paddingTop: spacing.md }} />
          <Button
            title="Confirm rejection"
            variant="danger"
            loading={decide.isPending}
            disabled={!reason.trim()}
            onPress={() => decide.mutate({ approve: false, rejectionReason: reason })}
          />
          <Button title="Cancel" variant="ghost" onPress={() => setRejecting(false)} />
        </View>
      ) : (
        <View style={{ gap: spacing.md }}>
          <Button title="Approve agent" loading={decide.isPending} onPress={() => decide.mutate({ approve: true })} />
          <Button title="Reject" variant="secondary" onPress={() => setRejecting(true)} />
        </View>
      )}
    </Screen>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <View style={{ flexDirection: 'row', justifyContent: 'space-between' }}>
      <Text variant="labelMd" color={colors.textMuted}>
        {label}
      </Text>
      <Text variant="labelMd" weight="semibold">
        {value}
      </Text>
    </View>
  );
}
