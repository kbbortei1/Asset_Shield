import { Ionicons } from '@expo/vector-icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { router, useLocalSearchParams } from 'expo-router';
import { useState } from 'react';
import { View } from 'react-native';
import { damageApi, isApiError, PairingSuggestion } from '@/lib/api';
import { Button, Card, EmptyState, ErrorState, EvidencePhoto, Header, ListSkeleton, RemoteImage, Screen, Text, useToast, showAlert } from '@/components/ui';
import { colors, radius, spacing } from '@/theme';

/** 0-100 confidence from GPS distance: 0m → 100%, 50m+ → floor. */
function matchPercent(distanceMeters?: number): number | null {
  if (typeof distanceMeters !== 'number') return null;
  return Math.max(8, Math.round(100 - (Math.min(distanceMeters, 50) / 50) * 92));
}

/**
 * Verify Evidence: pair a damage photo with the documented asset it shows.
 * Presents the proximity analysis and metadata that make the match credible,
 * instead of a bare list.
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
  // Defensive: never trust the shape enough to call .map on it directly — a
  // wrapped/!array payload used to crash the whole screen mid-render.
  const items = Array.isArray(suggestions.data) ? suggestions.data : [];
  const best = items.length > 0 ? items[0] : null;
  const bestPct = matchPercent(best?.distanceMeters);

  const pair = useMutation({
    mutationFn: (assetId: string) =>
      damageApi.pair(reportId, { damagePhotoId: photoId!, assetId, pairingMethod: 'GPS_AUTO' }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['report', reportId] });
      show('Evidence paired');
      router.back();
    },
    onError: (e) => {
      if (isApiError(e) && e.code === 'ALREADY_RESPONDED') router.back();
      else showAlert('Could not pair', isApiError(e) ? e.message : 'Try again.');
    },
    onSettled: () => setPairingId(null),
  });

  if (suggestions.isError) return <ErrorState onRetry={() => suggestions.refetch()} />;

  return (
    <Screen>
      <Header title="Verify evidence" />

      {photo ? (
        <Card padded={false} style={{ overflow: 'hidden' }}>
          <EvidencePhoto
            uri={photo.photoUrl}
            height={180}
            gpsLat={photo.gpsLat}
            gpsLng={photo.gpsLng}
            capturedAt={photo.capturedAt}
            verified={photo.sha256Hash}
            variant="incident"
            zoomable
          />
        </Card>
      ) : null}

      {best && bestPct !== null ? (
        <Card>
          <View style={{ flexDirection: 'row', justifyContent: 'space-between', marginBottom: spacing.sm }}>
            <Text variant="labelMd" color={colors.textMuted}>
              PROXIMITY ANALYSIS
            </Text>
            <Text variant="labelMd" weight="bold" color={colors.primary}>
              {bestPct}% match
            </Text>
          </View>
          <View style={{ height: 6, borderRadius: 3, backgroundColor: colors.border, overflow: 'hidden', marginBottom: spacing.sm }}>
            <View style={{ width: `${bestPct}%`, height: '100%', backgroundColor: colors.primary }} />
          </View>
          <Text variant="labelMd" color={colors.textMuted}>
            This damage photo was captured within ~{Math.round(best.distanceMeters ?? 0)}m of your closest documented
            asset. Both photos carry tamper-evident hashes.
          </Text>
        </Card>
      ) : (
        <Text variant="bodyMd" color={colors.textMuted}>
          Pair this damage photo with a documented asset to show before/after evidence. Unpaired photos still appear in
          the dossier, in a separate annex, but pairing makes the loss claim much stronger.
        </Text>
      )}

      {suggestions.isLoading ? (
        <ListSkeleton count={3} />
      ) : items.length === 0 ? (
        <EmptyState
          icon="git-compare-outline"
          title="No nearby matches"
          body="No documented assets were geo-tagged near this photo. It will still be included in the dossier as unpaired evidence."
        />
      ) : (
        items.map((s) => (
          <SuggestionCard
            key={s.assetId}
            s={s}
            busy={pairingId === s.assetId}
            onPair={() => {
              setPairingId(s.assetId);
              pair.mutate(s.assetId);
            }}
          />
        ))
      )}
    </Screen>
  );
}

function SuggestionCard({ s, busy, onPair }: { s: PairingSuggestion; busy: boolean; onPair: () => void }) {
  const pct = matchPercent(s.distanceMeters);
  return (
    <Card>
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: spacing.md }}>
        <View style={{ borderRadius: radius.md, overflow: 'hidden' }}>
          <RemoteImage uri={s.thumbnailUrl} width={64} height={64} radius={radius.md} />
          <View
            style={{
              position: 'absolute',
              bottom: 0,
              left: 0,
              right: 0,
              backgroundColor: 'rgba(10,16,14,0.62)',
              alignItems: 'center',
            }}
          >
            <Text variant="labelMd" color={colors.white} style={{ fontSize: 8, letterSpacing: 0.6 }}>
              BASELINE
            </Text>
          </View>
        </View>
        <View style={{ flex: 1, gap: 2 }}>
          <Text variant="bodyMd" weight="semibold" numberOfLines={1}>
            {s.description ?? 'Asset'}
          </Text>
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: spacing.sm }}>
            {typeof s.distanceMeters === 'number' ? (
              <Text variant="labelMd" color={colors.textMuted}>
                ~{Math.round(s.distanceMeters)}m away
              </Text>
            ) : null}
            {pct !== null ? (
              <View style={{ backgroundColor: colors.tealTint, borderRadius: radius.sm, paddingHorizontal: 6, paddingVertical: 1 }}>
                <Text variant="labelMd" color={colors.primary} style={{ fontSize: 10 }} weight="semibold">
                  {pct}%
                </Text>
              </View>
            ) : null}
            {s.capturedAt ? (
              <View style={{ flexDirection: 'row', alignItems: 'center', gap: 2 }}>
                <Ionicons name="checkmark-circle" size={12} color={colors.success} />
                <Text variant="labelMd" color={colors.textMuted} style={{ fontSize: 10 }}>
                  hashed
                </Text>
              </View>
            ) : null}
          </View>
        </View>
        <Button title="Pair" fullWidth={false} loading={busy} onPress={onPair} />
      </View>
    </Card>
  );
}
