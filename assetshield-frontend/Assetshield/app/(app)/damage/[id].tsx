import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { router, useLocalSearchParams } from 'expo-router';
import { Alert, View } from 'react-native';
import { damageApi, isApiError } from '@/lib/api';
import {
  Button,
  Card,
  EmptyState,
  ErrorState,
  Header,
  Loading,
  RemoteImage,
  Screen,
  StatusBadge,
  Text,
  VerifiedBadge,
  formatCedis,
} from '@/components/ui';
import { colors, spacing } from '@/theme';

/** DISASTER (beat 2): report detail — photos, pairing status, complete, generate dossier. */
export default function DamageReportDetail() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const reportId = id!;
  const qc = useQueryClient();
  const q = useQuery({ queryKey: ['report', reportId], queryFn: () => damageApi.get(reportId) });

  const complete = useMutation({
    mutationFn: () => damageApi.complete(reportId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['report', reportId] }),
    onError: (e) => {
      if (isApiError(e) && e.code === 'EMPTY_REPORT') Alert.alert('Add a photo first', 'A report needs at least one photo before completing.');
      else Alert.alert('Could not complete', isApiError(e) ? e.message : 'Try again.');
    },
  });

  const generate = useMutation({
    mutationFn: () => damageApi.generateDossier(reportId),
    onSuccess: (res) =>
      router.push(
        `/(app)/dossier/${res.dossierId}?ref=${encodeURIComponent(res.payment?.reference ?? '')}&url=${encodeURIComponent(
          res.payment?.authorizationUrl ?? '',
        )}` as never,
      ),
    onError: (e) => {
      if (isApiError(e) && e.code === 'DOSSIER_EXISTS') Alert.alert('Dossier exists', 'A dossier already exists for this report.');
      else Alert.alert('Could not start', isApiError(e) ? e.message : 'Try again.');
    },
  });

  if (q.isLoading) return <Loading />;
  if (q.isError) return <ErrorState onRetry={() => q.refetch()} />;
  const r = q.data!;
  const photos = r.photos ?? [];
  const isCompleted = r.status === 'COMPLETED';

  return (
    <Screen
      refreshing={q.isRefetching}
      onRefresh={q.refetch}
      footer={
        isCompleted ? (
          <Button title="Generate dossier" loading={generate.isPending} onPress={() => generate.mutate()} />
        ) : (
          <Button title="Complete report" loading={complete.isPending} disabled={photos.length === 0} onPress={() => complete.mutate()} />
        )
      }
    >
      <Header title={`${r.disasterType} report`} />

      <Card>
        <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
          <View style={{ flex: 1 }}>
            <Text variant="headlineSm">{r.propertyName ?? 'Damage report'}</Text>
            <Text variant="labelMd" color={colors.textMuted}>
              {r.description ?? r.disasterType} · {r.occurredAt ? new Date(r.occurredAt).toLocaleDateString() : ''}
            </Text>
          </View>
          <StatusBadge status={isCompleted ? 'secured' : 'needsUpdate'} label={r.status} />
        </View>
        {isCompleted && typeof r.totalEstimatedLoss === 'number' ? (
          <View style={{ marginTop: spacing.md }}>
            <Text variant="labelMd" color={colors.textMuted}>
              Computed loss
            </Text>
            <Text variant="currencyDisplay" color={colors.primary}>
              {formatCedis(r.totalEstimatedLoss)}
            </Text>
          </View>
        ) : null}
      </Card>

      <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
        <Text variant="headlineSm">Damage photos</Text>
        {!isCompleted ? (
          <Button title="Add photo" fullWidth={false} variant="secondary" onPress={() => router.push(`/(app)/damage/${reportId}/capture` as never)} />
        ) : null}
      </View>

      {isCompleted ? (
        <Card style={{ backgroundColor: colors.tealTint }}>
          <Text variant="labelMd" color={colors.primary}>
            This report is completed and locked. Generate a dossier to share it with an agent.
          </Text>
        </Card>
      ) : null}

      {photos.length === 0 ? (
        <EmptyState
          icon="camera-outline"
          title="No photos yet"
          body="Capture damage photos. We'll suggest matching assets by location."
          actionLabel={isCompleted ? undefined : 'Add a photo'}
          onAction={isCompleted ? undefined : () => router.push(`/(app)/damage/${reportId}/capture` as never)}
        />
      ) : (
        <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: spacing.md }}>
          {photos.map((ph) => {
            const canPair = !isCompleted && !ph.paired;
            return (
              <Card
                key={ph.id}
                padded={false}
                style={{ width: '47%', overflow: 'hidden' }}
                onPress={canPair ? () => router.push(`/(app)/damage/${reportId}/pair?photoId=${ph.id}` as never) : undefined}
              >
                <RemoteImage uri={ph.photoUrl} height={120} zoomable={!canPair} />
                <View style={{ padding: spacing.md, gap: spacing.xs }}>
                  <VerifiedBadge hash={ph.sha256Hash} />
                  <Text variant="labelMd" color={ph.paired ? colors.success : colors.cta}>
                    {ph.paired ? 'Paired' : isCompleted ? 'Unpaired' : 'Unpaired · Tap to pair'}
                  </Text>
                </View>
              </Card>
            );
          })}
        </View>
      )}
    </Screen>
  );
}
