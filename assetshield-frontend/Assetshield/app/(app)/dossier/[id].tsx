import { Ionicons } from '@expo/vector-icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { File, Paths } from 'expo-file-system';
import * as Sharing from 'expo-sharing';
import * as WebBrowser from 'expo-web-browser';
import { router, useLocalSearchParams } from 'expo-router';
import { useState } from 'react';
import { Alert, View } from 'react-native';
import { damageApi, DossierStatus, isApiError, resolveMediaUrl } from '@/lib/api';
import { runCheckout } from '@/lib/payments/checkout';
import { Button, Card, ErrorState, Header, Hero, Loading, Screen, Text } from '@/components/ui';
import { colors, spacing } from '@/theme';

/** DOSSIER (beat 3) + CONTROL (beat 5): pay → poll → download → share/rotate. */
export default function DossierScreen() {
  const { id, ref, url } = useLocalSearchParams<{ id: string; ref?: string; url?: string }>();
  const dossierId = id!;
  const qc = useQueryClient();
  const [paying, setPaying] = useState(false);

  const q = useQuery({
    queryKey: ['dossier-status', dossierId],
    queryFn: () => damageApi.dossierStatus(dossierId),
    refetchInterval: (query) => {
      const s = query.state.data?.status;
      return s === 'READY' || s === 'FAILED' ? false : 2000;
    },
  });

  const pay = async () => {
    if (!ref) {
      Alert.alert('Payment unavailable', 'Open this dossier from the report you just generated to complete payment.');
      return;
    }
    setPaying(true);
    try {
      const result = await runCheckout({ reference: ref, authorizationUrl: url ?? '' });
      if (result === 'failed') Alert.alert('Payment failed', 'Please try again.');
      qc.invalidateQueries({ queryKey: ['dossier-status', dossierId] });
    } finally {
      setPaying(false);
    }
  };

  const [downloading, setDownloading] = useState(false);

  const download = async () => {
    setDownloading(true);
    try {
      const { downloadUrl, fileName } = await damageApi.dossierDownload(dossierId); // fresh signed URL each time
      const url = resolveMediaUrl(downloadUrl);
      if (!url) throw new Error('No download URL');

      const name = /\.pdf$/i.test(fileName ?? '') ? fileName : `${fileName || `dossier-${dossierId}`}.pdf`;
      const dest = new File(Paths.cache, name);
      if (dest.exists) dest.delete();
      const file = await File.downloadFileAsync(url, dest);

      if (await Sharing.isAvailableAsync()) {
        await Sharing.shareAsync(file.uri, { mimeType: 'application/pdf', dialogTitle: name, UTI: 'com.adobe.pdf' });
      } else {
        await WebBrowser.openBrowserAsync(url); // fallback: view in browser
      }
    } catch (e) {
      if (isApiError(e) && e.code === 'PAYMENT_REQUIRED') Alert.alert('Payment required', 'Complete payment to download.');
      else Alert.alert('Could not download', isApiError(e) ? e.message : 'Please try again.');
    } finally {
      setDownloading(false);
    }
  };

  const rotate = useMutation({
    mutationFn: () => damageApi.rotateShareToken(dossierId),
    onSuccess: () => Alert.alert('Link rotated', 'Any previously shared public link no longer works.'),
    onError: (e) => Alert.alert('Could not rotate', isApiError(e) ? e.message : 'Try again.'),
  });

  const retry = useMutation({
    mutationFn: () => damageApi.retryGeneration(dossierId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['dossier-status', dossierId] }),
    onError: (e) => Alert.alert('Could not retry', isApiError(e) ? e.message : 'Try again.'),
  });

  if (q.isLoading) return <Loading label="Checking dossier…" />;
  if (q.isError) return <ErrorState onRetry={() => q.refetch()} />;
  const status = q.data!.status;

  return (
    <Screen>
      <Header title="Dossier" />
      <StatusCard status={status} />

      {status === 'PENDING_PAYMENT' ? (
        <Button title="Pay & generate dossier" loading={paying} onPress={pay} />
      ) : null}

      {status === 'GENERATING' ? (
        <Card>
          <View style={{ flexDirection: 'row', gap: spacing.md, alignItems: 'center' }}>
            <Loading />
            <Text variant="bodyMd" color={colors.textMuted} style={{ flex: 1 }}>
              Building your tamper-evident dossier. This usually takes a few seconds.
            </Text>
          </View>
        </Card>
      ) : null}

      {status === 'READY' ? (
        <View style={{ gap: spacing.md }}>
          <Button title="Download dossier (PDF)" loading={downloading} onPress={download} leftIcon={<Ionicons name="download" size={18} color={colors.onCta} />} />
          <Button title="Share with an agent" variant="secondary" onPress={() => router.push(`/(app)/dossier/${dossierId}/share` as never)} />
          <Button title="Rotate share link" variant="ghost" loading={rotate.isPending} onPress={() => rotate.mutate()} />
        </View>
      ) : null}

      {status === 'FAILED' ? (
        <View style={{ gap: spacing.md }}>
          <Card style={{ backgroundColor: colors.error }}>
            <Text variant="bodyMd" color={colors.white}>
              Generation failed. You can retry without being charged again.
            </Text>
          </Card>
          <Button title="Retry generation" loading={retry.isPending} onPress={() => retry.mutate()} />
        </View>
      ) : null}
    </Screen>
  );
}

const STATUS_META: Record<DossierStatus, { icon: keyof typeof Ionicons.glyphMap; label: string }> = {
  PENDING_PAYMENT: { icon: 'card', label: 'Awaiting payment' },
  GENERATING: { icon: 'cog', label: 'Generating' },
  READY: { icon: 'checkmark-circle', label: 'Ready' },
  FAILED: { icon: 'close-circle', label: 'Failed' },
};

function StatusCard({ status }: { status: DossierStatus }) {
  const m = STATUS_META[status];
  return (
    <Hero>
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: spacing.md }}>
        <Ionicons name={m.icon} size={32} color={colors.cta} />
        <View>
          <Text variant="labelMd" color={colors.tealMuted}>
            Dossier status
          </Text>
          <Text variant="headlineSm" color={colors.onPrimary}>
            {m.label}
          </Text>
        </View>
      </View>
    </Hero>
  );
}
