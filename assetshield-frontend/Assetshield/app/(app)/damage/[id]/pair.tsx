import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { router, useLocalSearchParams } from 'expo-router';
import { useState } from 'react';
import { Alert, View } from 'react-native';
import { damageApi, isApiError } from '@/lib/api';
import { Button, Card, EmptyState, ErrorState, Header, ListSkeleton, RemoteImage, Screen, Text, useToast } from '@/components/ui';
import { colors, radius, spacing } from '@/theme';

/**
 * Resume pairing for a photo that was skipped (or had no matches) at capture
 * time. Reached by tapping an unpaired photo on the report detail.
 */
export default function PairPhoto() {
  const { id, photoId } = useLocalSearchParams<{ id: string; photoId: string }>();
  const reportId = id!;
  const qc = useQueryClient();
  const { show } = useToast();
  const [pairingId, setPairingId] = useState<string | null>(null);

  const report = useQuery({ queryKey: ['report', reportId], queryFn: () => damageApi.get(reportId) });
  const suggestions = useQuery({
    queryKey: ['pairing-suggestions', reportId, photoId],
    queryFn: () => damageApi.pairingSuggestions(reportId, photoId!),
    enabled: !!photoId,
  });

  const photo = (report.data?.photos ?? []).find((p) => p.id === photoId);

  const pair = useMutation({
    mutationFn: (assetId: string) =>
      damageApi.pair(reportId, { damagePhotoId: photoId!, assetId, pairingMethod: 'GPS_AUTO' }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['report', reportId] });
      show('Photo paired');
      router.back();
    },
    onError: (e) => {
      if (isApiError(e) && e.code === 'ALREADY_RESPONDED') router.back();
      else Alert.alert('Could not pair', isApiError(e) ? e.message : 'Try again.');
    },
    onSettled: () => setPairingId(null),
  });

  if (suggestions.isError) return <ErrorState onRetry={() => suggestions.refetch()} />;

  return (
    <Screen>
      <Header title="Pair with an asset" />
      {photo ? (
        <Card padded={false} style={{ overflow: 'hidden' }}>
          <RemoteImage uri={photo.photoUrl} height={180} zoomable />
        </Card>
      ) : null}
      <Text variant="bodyMd" color={colors.textMuted}>
        Pair this damage photo with a documented asset to show before/after evidence. Unpaired photos still appear in
        the dossier, in a separate annex, but pairing makes the loss claim much stronger.
      </Text>

      {suggestions.isLoading ? (
        <ListSkeleton count={3} />
      ) : (suggestions.data ?? []).length === 0 ? (
        <EmptyState
          icon="git-compare-outline"
          title="No nearby matches"
          body="No documented assets were geo-tagged near this photo. It will still be included in the dossier as unpaired evidence."
        />
      ) : (
        (suggestions.data ?? []).map((s) => (
          <Card key={s.assetId}>
            <View style={{ flexDirection: 'row', alignItems: 'center', gap: spacing.md }}>
              <RemoteImage uri={s.thumbnailUrl} width={56} height={56} radius={radius.md} />
              <View style={{ flex: 1 }}>
                <Text variant="bodyMd" weight="semibold">
                  {s.description ?? 'Asset'}
                </Text>
                {typeof s.distanceMeters === 'number' ? (
                  <Text variant="labelMd" color={colors.textMuted}>
                    ~{Math.round(s.distanceMeters)}m away
                  </Text>
                ) : null}
              </View>
              <Button
                title="Pair"
                fullWidth={false}
                loading={pairingId === s.assetId}
                onPress={() => {
                  setPairingId(s.assetId);
                  pair.mutate(s.assetId);
                }}
              />
            </View>
          </Card>
        ))
      )}
    </Screen>
  );
}
