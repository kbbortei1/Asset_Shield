import { Ionicons } from '@expo/vector-icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { View } from 'react-native';
import { isApiError, propertiesApi } from '@/lib/api';
import { Button, Card, EmptyState, ErrorState, Header, Loading, Screen, Text } from '@/components/ui';
import { colors, spacing } from '@/theme';

/** Household invitations addressed to me — accept or decline. */
export default function Invitations() {
  const qc = useQueryClient();
  const q = useQuery({ queryKey: ['invitations'], queryFn: () => propertiesApi.myInvitations() });

  const respond = useMutation({
    mutationFn: ({ id, accept }: { id: string; accept: boolean }) => propertiesApi.respondInvitation(id, accept),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['invitations'] });
      qc.invalidateQueries({ queryKey: ['properties'] });
    },
    onError: (e) => {
      if (isApiError(e) && e.code === 'ALREADY_RESPONDED') qc.invalidateQueries({ queryKey: ['invitations'] });
    },
  });

  const items = q.data ?? [];

  return (
    <Screen refreshing={q.isRefetching} onRefresh={q.refetch}>
      <Header title="Invitations" />
      {q.isLoading ? (
        <Loading />
      ) : q.isError ? (
        <ErrorState onRetry={() => q.refetch()} />
      ) : items.length === 0 ? (
        <EmptyState icon="mail-outline" title="No invitations" body="When someone invites you to a household, it appears here." />
      ) : (
        items.map((i) => (
          <Card key={i.id}>
            <View style={{ gap: spacing.md }}>
              <View style={{ flexDirection: 'row', alignItems: 'center', gap: spacing.md }}>
                <Ionicons name="people" size={26} color={colors.primary} />
                <View style={{ flex: 1 }}>
                  <Text variant="bodyMd" weight="semibold">
                    {i.propertyName ?? 'A property'}
                  </Text>
                  <Text variant="labelMd" color={colors.textMuted}>
                    Invited by {i.ownerName ?? 'the owner'}
                  </Text>
                </View>
              </View>
              <View style={{ flexDirection: 'row', gap: spacing.md }}>
                <Button title="Decline" variant="secondary" loading={respond.isPending} onPress={() => respond.mutate({ id: i.id, accept: false })} />
                <Button title="Accept" loading={respond.isPending} onPress={() => respond.mutate({ id: i.id, accept: true })} />
              </View>
            </View>
          </Card>
        ))
      )}
    </Screen>
  );
}
