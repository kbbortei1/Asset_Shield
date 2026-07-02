import { Ionicons } from '@expo/vector-icons';
import { useQuery } from '@tanstack/react-query';
import { router, useLocalSearchParams } from 'expo-router';
import { View } from 'react-native';
import { marketplaceApi } from '@/lib/api';
import { Button, Card, ErrorState, Header, Loading, Screen, Text } from '@/components/ui';
import { colors, spacing } from '@/theme';

/** CONSENT (beat 4, agent): verify a shared dossier's integrity, then quote. Stitch: "Shared Dossier Viewer". */
export default function SharedDossierViewer() {
  const { dossierId } = useLocalSearchParams<{ dossierId: string }>();
  const q = useQuery({ queryKey: ['verify', dossierId], queryFn: () => marketplaceApi.verifyDossier(dossierId!) });

  if (q.isLoading) return <Loading label="Verifying integrity…" />;
  if (q.isError) return <ErrorState message="This dossier may have been revoked." onRetry={() => q.refetch()} />;
  const v = q.data!;
  const ok = v.tamperEvident === true;

  return (
    <Screen footer={<Button title="Send a quote" onPress={() => router.push(`/(app)/agent/quote/${dossierId}` as never)} />}>
      <Header title="Shared dossier" />

      <Card style={{ backgroundColor: ok ? colors.success : colors.error }}>
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: spacing.md }}>
          <Ionicons name={ok ? 'shield-checkmark' : 'shield'} size={32} color={colors.white} />
          <View style={{ flex: 1 }}>
            <Text variant="headlineSm" color={colors.white}>
              {ok ? 'Tamper-evident: verified' : 'Integrity check failed'}
            </Text>
            <Text variant="labelMd" color={colors.white}>
              {ok
                ? 'The recomputed manifest hash matches the original.'
                : `${v.mismatches?.length ?? 0} mismatch(es) found — treat with caution.`}
            </Text>
          </View>
        </View>
      </Card>

      <Card>
        <View style={{ gap: spacing.sm }}>
          <Row label="Photos" value={String(v.photoCount ?? '—')} />
          <Row label="Manifest hash" value={v.manifestHash ? `${v.manifestHash.slice(0, 10)}…` : '—'} />
          <Row label="Recomputed" value={v.recomputedHash ? `${v.recomputedHash.slice(0, 10)}…` : '—'} />
          <Row label="Verified" value={v.verifiedAt ? new Date(v.verifiedAt).toLocaleString() : '—'} />
        </View>
      </Card>
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
