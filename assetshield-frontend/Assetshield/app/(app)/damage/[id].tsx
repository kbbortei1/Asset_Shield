import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { router, useLocalSearchParams } from 'expo-router';
import { View } from 'react-native';
import { damageApi, isApiError } from '@/lib/api';
import { Button, Card, EmptyState, ErrorState, EvidencePhoto, Header, Loading, Screen, StatusBadge, Text, VerifiedBadge, formatCedis, showAlert } from '@/components/ui';
import { colors, spacing } from '@/theme';

/**
 * Sealing a report is the most anxious moment in the app: something bad has
 * happened, and the owner has just locked their only evidence of it. A single
 * "generate a dossier" line left them guessing what the rest of the journey is,
 * so spell out all three steps at the point they're asking the question.
 */
function WhatHappensNext() {
  const steps: [string, string][] = [
    ['Generate the dossier', 'A tamper-evident PDF holding every photo and its hash.'],
    ['Share it with an agent', 'Only agents whose interest you accepted can open it, and you can revoke that at any time.'],
    ['Receive a quote', 'The agent verifies the dossier is unaltered, then sends you an offer.'],
  ];
  return (
    <Card style={{ backgroundColor: colors.tealTint, gap: spacing.md }}>
      <Text variant="labelMd" weight="semibold" color={colors.primary}>
        Sealed. Here's what happens next
      </Text>
      {steps.map(([title, body], i) => (
        <View key={title} style={{ flexDirection: 'row', gap: spacing.md, alignItems: 'flex-start' }}>
          <View
            style={{
              width: 22,
              height: 22,
              borderRadius: 11,
              backgroundColor: colors.primary,
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            <Text variant="labelMd" weight="semibold" color={colors.onPrimary} style={{ fontSize: 11 }}>
              {i + 1}
            </Text>
          </View>
          <View style={{ flex: 1, gap: 1 }}>
            <Text variant="labelMd" weight="semibold" color={colors.primary}>
              {title}
            </Text>
            <Text variant="labelMd" color={colors.textMuted}>
              {body}
            </Text>
          </View>
        </View>
      ))}
    </Card>
  );
}

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
      if (isApiError(e) && e.code === 'EMPTY_REPORT') showAlert('Add a photo first', 'A report needs at least one photo before completing.');
      else showAlert('Could not complete', isApiError(e) ? e.message : 'Try again.');
    },
  });

  const generate = useMutation({
    mutationFn: () => damageApi.generateDossier(reportId),
    // the dossier screen fetches its own fresh checkout handle when unpaid
    onSuccess: (res) => router.push(`/(app)/dossier/${res.dossierId}` as never),
    onError: (e) => {
      if (isApiError(e) && e.code === 'DOSSIER_EXISTS') {
        const existingId = e.fieldErrors?.dossierId;
        if (existingId) {
          // jump to the existing dossier — it offers Pay if still unpaid
          router.push(`/(app)/dossier/${existingId}` as never);
          return;
        }
        showAlert('Dossier exists', 'A dossier already exists for this report. Find it under Dossiers.');
      } else showAlert('Could not start', isApiError(e) ? e.message : 'Try again.');
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
          <Button
            title="Complete report"
            loading={complete.isPending}
            disabled={photos.length === 0}
            onPress={() => {
              const paired = photos.filter((ph) => ph.paired).length;
              showAlert(
                'Complete this report?',
                `${photos.length} photo${photos.length === 1 ? '' : 's'} (${paired} paired with documented assets).\n\n` +
                  'Once completed, the report is sealed as immutable evidence: no photos can be added or changed, and the estimated loss is computed.',
                [
                  { text: 'Keep editing', style: 'cancel' },
                  { text: 'Complete & seal', onPress: () => complete.mutate() },
                ],
              );
            }}
          />
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

      {isCompleted ? <WhatHappensNext /> : null}

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
            // paired photos open the before/after comparison; unpaired (in a
            // draft) resume pairing
            const onPress = canPair
              ? () => router.push(`/(app)/damage/${reportId}/pair?photoId=${ph.id}` as never)
              : ph.paired
                ? () => router.push(`/(app)/damage/${reportId}/compare?photoId=${ph.id}` as never)
                : undefined;
            return (
              <Card key={ph.id} padded={false} style={{ width: '47%', overflow: 'hidden' }} onPress={onPress}>
                <EvidencePhoto
                  uri={ph.photoUrl}
                  height={120}
                  gpsLat={ph.gpsLat}
                  gpsLng={ph.gpsLng}
                  capturedAt={ph.capturedAt}
                  verified={ph.sha256Hash}
                  variant="incident"
                />
                <View style={{ padding: spacing.md, gap: spacing.xs }}>
                  <VerifiedBadge hash={ph.sha256Hash} />
                  <Text variant="labelMd" color={ph.paired ? colors.success : colors.cta}>
                    {ph.paired ? 'Paired · Before/After' : isCompleted ? 'Unpaired' : 'Unpaired · Tap to pair'}
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
