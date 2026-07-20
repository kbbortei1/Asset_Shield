import { Ionicons } from '@expo/vector-icons';
import { useQuery } from '@tanstack/react-query';
import { useLocalSearchParams } from 'expo-router';
import { View } from 'react-native';
import { damageApi, propertiesApi } from '@/lib/api';
import { Card, EmptyState, ErrorState, EvidencePhoto, Header, Loading, Screen, Text, ValuePill } from '@/components/ui';
import { colors, spacing } from '@/theme';

/**
 * Before/After evidence comparison for a PAIRED damage photo: the documented
 * asset (baseline) against the damage capture, with the pairing metadata that
 * makes the match credible.
 */
export default function CompareEvidence() {
  const { id, photoId } = useLocalSearchParams<{ id: string; photoId: string }>();
  const reportId = id!;

  const report = useQuery({ queryKey: ['report', reportId], queryFn: () => damageApi.get(reportId) });
  const photo = (report.data?.photos ?? []).find((p) => p.id === photoId);
  const pair = (report.data?.pairs ?? []).find((p) => p.damagePhotoId === photoId);

  const asset = useQuery({
    queryKey: ['asset', pair?.assetId],
    queryFn: () => propertiesApi.getAsset(pair!.assetId),
    enabled: !!pair?.assetId,
  });

  if (report.isLoading || asset.isLoading) return <Loading label="Loading evidence…" />;
  if (report.isError) return <ErrorState onRetry={() => report.refetch()} />;
  if (!photo || !pair) {
    return (
      <Screen>
        <Header title="Before & after" />
        <EmptyState icon="git-compare-outline" title="No pairing found" body="This photo is not paired with a documented asset." />
      </Screen>
    );
  }
  const a = asset.data;

  return (
    <Screen>
      <Header title="Before & after" />
      <Text variant="bodyMd" color={colors.textMuted}>
        Both photos are hashed at capture. This comparison is what an insurer sees as evidence of the loss.
      </Text>

      <View style={{ gap: spacing.xs }}>
        <Text variant="labelMd" weight="semibold" color={colors.success}>
          BASELINE · DOCUMENTED ASSET
        </Text>
        <Card padded={false} style={{ overflow: 'hidden' }}>
          <EvidencePhoto
            uri={a?.photoUrl}
            height={190}
            gpsLat={a?.gpsLat}
            gpsLng={a?.gpsLng}
            capturedAt={a?.capturedAt}
            verified={a?.sha256Hash}
            zoomable
          />
          <View style={{ padding: spacing.md, flexDirection: 'row', alignItems: 'center', gap: spacing.md }}>
            <View style={{ flex: 1 }}>
              <Text variant="bodyMd" weight="semibold" numberOfLines={1}>
                {a?.description ?? 'Documented asset'}
              </Text>
              {a?.capturedAt ? (
                <Text variant="labelMd" color={colors.textMuted}>
                  Documented {new Date(a.capturedAt).toLocaleDateString()}
                </Text>
              ) : null}
            </View>
            {typeof a?.estimatedValue === 'number' ? <ValuePill amount={a.estimatedValue} /> : null}
          </View>
        </Card>
      </View>

      <View style={{ alignItems: 'center' }}>
        <View
          style={{
            width: 36,
            height: 36,
            borderRadius: 18,
            backgroundColor: colors.card,
            borderWidth: 1,
            borderColor: colors.border,
            alignItems: 'center',
            justifyContent: 'center',
          }}
        >
          <Ionicons name="swap-vertical" size={18} color={colors.primary} />
        </View>
      </View>

      <View style={{ gap: spacing.xs }}>
        <Text variant="labelMd" weight="semibold" color={colors.error}>
          CURRENT · CAPTURED DAMAGE
        </Text>
        <Card padded={false} style={{ overflow: 'hidden' }}>
          <EvidencePhoto
            uri={photo.photoUrl}
            height={190}
            gpsLat={photo.gpsLat}
            gpsLng={photo.gpsLng}
            capturedAt={photo.capturedAt}
            verified={photo.sha256Hash}
            variant="incident"
            zoomable
          />
          {photo.description ? (
            <View style={{ padding: spacing.md }}>
              <Text variant="labelMd" color={colors.textMuted}>
                {photo.description}
              </Text>
            </View>
          ) : null}
        </Card>
      </View>

      <Card>
        <Text variant="labelMd" color={colors.textMuted} style={{ marginBottom: spacing.sm }}>
          Pairing metadata
        </Text>
        <View style={{ gap: spacing.sm }}>
          <MetaRow
            label="Captured distance apart"
            value={typeof pair.distanceMeters === 'number' ? `~${Math.round(pair.distanceMeters)}m` : 'GPS matched'}
            good
          />
          <MetaRow label="Pairing method" value={pair.pairingMethod === 'GPS_AUTO' ? 'GPS proximity' : 'Manual'} />
          <MetaRow label="Baseline hash" value={a?.sha256Hash ? `${a.sha256Hash.slice(0, 12)}…` : '-'} good={!!a?.sha256Hash} />
          <MetaRow label="Damage hash" value={`${photo.sha256Hash.slice(0, 12)}…`} good />
        </View>
      </Card>
    </Screen>
  );
}

function MetaRow({ label, value, good }: { label: string; value: string; good?: boolean }) {
  return (
    <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
      <Text variant="labelMd" color={colors.textMuted}>
        {label}
      </Text>
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: 4 }}>
        {good ? <Ionicons name="checkmark-circle" size={14} color={colors.success} /> : null}
        <Text variant="labelMd" weight="semibold">
          {value}
        </Text>
      </View>
    </View>
  );
}
