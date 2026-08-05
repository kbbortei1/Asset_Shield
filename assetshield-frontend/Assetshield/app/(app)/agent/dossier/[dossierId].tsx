import { Ionicons } from '@expo/vector-icons';
import { useQuery } from '@tanstack/react-query';
import { router, useLocalSearchParams } from 'expo-router';
import { useState } from 'react';
import * as WebBrowser from 'expo-web-browser';
import { View } from 'react-native';

import { isApiError, marketplaceApi, resolveMediaUrl } from '@/lib/api';
import { Button, Card, ErrorState, Header, Loading, Screen, Text, formatCedis, showAlert } from '@/components/ui';
import { colors, spacing } from '@/theme';

/**
 * CONSENT (beat 4, agent): the agent's view of a shared dossier. Shows what the
 * claim is about, lets the agent open the actual PDF, proves it is unaltered,
 * then quotes. Before, this screen showed only a hash and a photo count with no
 * way to open the evidence — an insurer could not do their job.
 */
export default function SharedDossierViewer() {
  const params = useLocalSearchParams<{
    dossierId: string;
    property?: string;
    owner?: string;
    disaster?: string;
    loss?: string;
    interestId?: string;
  }>();
  const dossierId = params.dossierId!;

  const q = useQuery({ queryKey: ['verify', dossierId], queryFn: () => marketplaceApi.verifyDossier(dossierId) });
  const [opening, setOpening] = useState(false);

  const openPdf = async () => {
    setOpening(true);
    try {
      const { downloadUrl } = await marketplaceApi.agentDownloadDossier(dossierId); // fresh signed URL
      const url = resolveMediaUrl(downloadUrl);
      if (!url) throw new Error('No download URL');
      // Render the pages inline in an in-app browser tab (Android downloads a
      // raw PDF URL otherwise) — same in-app experience the owner gets.
      const inline = `https://docs.google.com/viewer?embedded=true&url=${encodeURIComponent(url)}`;
      await WebBrowser.openBrowserAsync(inline, {
        toolbarColor: colors.primary,
        controlsColor: colors.onPrimary,
        enableBarCollapsing: true,
      });
    } catch (e) {
      showAlert(
        'Could not open the dossier',
        isApiError(e) ? e.message : 'The owner may have revoked access. Pull to refresh and try again.',
      );
    } finally {
      setOpening(false);
    }
  };

  if (q.isLoading) return <Loading label="Loading shared dossier…" />;
  if (q.isError) return <ErrorState message="This dossier may have been revoked." onRetry={() => q.refetch()} />;
  const v = q.data!;
  const ok = v.tamperEvident === true;
  const loss = params.loss ? Number(params.loss) : undefined;

  return (
    <Screen
      footer={
        <View style={{ gap: spacing.md }}>
          <Button title="Send a quote" onPress={() => router.push(`/(app)/agent/quote/${dossierId}` as never)} />
          {params.interestId ? (
            <Button
              title="Chat with owner"
              variant="secondary"
              leftIcon={<Ionicons name="chatbubble-ellipses-outline" size={18} color={colors.primary} />}
              onPress={() =>
                router.push(`/(app)/chat/${params.interestId}?name=${encodeURIComponent(params.owner ?? 'Owner')}` as never)
              }
            />
          ) : null}
        </View>
      }
    >
      <Header title="Shared dossier" />

      {/* What the claim is about — the context an insurer needs before quoting. */}
      <Card>
        <View style={{ gap: spacing.xs }}>
          <Text variant="headlineSm">{params.property ?? 'Shared dossier'}</Text>
          {params.owner ? (
            <Text variant="labelMd" color={colors.textMuted}>
              Owner: {params.owner}
            </Text>
          ) : null}
          <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: spacing.md, marginTop: spacing.xs }}>
            {params.disaster ? <Meta icon="flame-outline" label={params.disaster} /> : null}
            <Meta icon="images-outline" label={`${v.photoCount ?? 0} photo${v.photoCount === 1 ? '' : 's'}`} />
            {typeof loss === 'number' && !Number.isNaN(loss) ? (
              <Meta icon="cash-outline" label={`Claimed ${formatCedis(loss)}`} />
            ) : null}
          </View>
        </View>
      </Card>

      {/* Open the actual evidence. */}
      <Button title="View full dossier (PDF)" loading={opening} onPress={openPdf} />

      {/* Integrity: is what I'm about to quote on genuine and unaltered? */}
      <Card style={{ backgroundColor: ok ? colors.success : colors.error }}>
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: spacing.md }}>
          <Ionicons name={ok ? 'shield-checkmark' : 'shield'} size={32} color={colors.white} />
          <View style={{ flex: 1 }}>
            <Text variant="headlineSm" color={colors.white}>
              {ok ? 'Tamper-evident: verified' : 'Integrity check failed'}
            </Text>
            <Text variant="labelMd" color={colors.white}>
              {ok
                ? 'The recomputed manifest hash matches the original. Nothing has been altered.'
                : `${v.mismatches?.length ?? 0} mismatch(es) found. Treat with caution.`}
            </Text>
          </View>
        </View>
      </Card>

      <Card>
        <View style={{ gap: spacing.sm }}>
          <Row label="Manifest hash" value={v.manifestHash ? `${v.manifestHash.slice(0, 10)}…` : '-'} />
          <Row label="Recomputed" value={v.recomputedHash ? `${v.recomputedHash.slice(0, 10)}…` : '-'} />
          <Row label="Verified" value={v.verifiedAt ? new Date(v.verifiedAt).toLocaleString() : '-'} />
        </View>
      </Card>
    </Screen>
  );
}

function Meta({ icon, label }: { icon: keyof typeof Ionicons.glyphMap; label: string }) {
  return (
    <View style={{ flexDirection: 'row', alignItems: 'center', gap: 4 }}>
      <Ionicons name={icon} size={15} color={colors.textMuted} />
      <Text variant="labelMd" color={colors.textMuted}>
        {label}
      </Text>
    </View>
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
