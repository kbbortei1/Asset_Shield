import { Ionicons } from '@expo/vector-icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { File, Paths } from 'expo-file-system';
import * as Sharing from 'expo-sharing';
import * as WebBrowser from 'expo-web-browser';
import { router, useLocalSearchParams } from 'expo-router';
import { useMemo, useState } from 'react';
import { Alert, Pressable, View } from 'react-native';
import {
  Asset,
  DamagePhoto,
  damageApi,
  DossierStatus,
  isApiError,
  Pair,
  propertiesApi,
  resolveMediaUrl,
} from '@/lib/api';
import { runCheckout } from '@/lib/payments/checkout';
import { Button, Card, ErrorState, EvidencePhoto, Header, Hero, Loading, Screen, SectionHeader, Text, useToast } from '@/components/ui';
import type { EvidencePhotoProps } from '@/components/ui/EvidencePhoto';
import { colors, radius, spacing } from '@/theme';

const DISASTER_META: Record<string, { icon: keyof typeof Ionicons.glyphMap; label: string }> = {
  FIRE: { icon: 'flame', label: 'Fire' },
  FLOOD: { icon: 'water', label: 'Flood' },
  THEFT: { icon: 'lock-open', label: 'Theft' },
  STORM: { icon: 'thunderstorm', label: 'Storm' },
  OTHER: { icon: 'alert-circle', label: 'Incident' },
};

const cedis = (n?: number) =>
  n == null ? '—' : `₵${n.toLocaleString(undefined, { maximumFractionDigits: 0 })}`;

const shortDate = (iso?: string) => {
  if (!iso) return '';
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? '' : d.toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' });
};

/** DOSSIER (beat 3) + CONTROL (beat 5): pay → poll → view in-app → download → share/rotate. */
export default function DossierScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const dossierId = id!;
  const qc = useQueryClient();
  const { show } = useToast();
  const [paying, setPaying] = useState(false);
  const [downloading, setDownloading] = useState(false);
  const [viewing, setViewing] = useState(false);

  const q = useQuery({
    queryKey: ['dossier-status', dossierId],
    queryFn: () => damageApi.dossierStatus(dossierId),
    refetchInterval: (query) => {
      const s = query.state.data?.status;
      return s === 'READY' || s === 'FAILED' ? false : 2000;
    },
  });
  const status = q.data?.status;
  const ready = status === 'READY';

  // Once ready, resolve what's IN the dossier so the owner can SEE it in-app
  // (not just download a PDF). The status endpoint has no report link, so we
  // find this dossier in the owner's list to get its damageReportId + metadata.
  const meta = useQuery({
    queryKey: ['dossier-meta', dossierId],
    queryFn: async () => (await damageApi.myDossiers({ size: 50 })).items.find((d) => d.id === dossierId) ?? null,
    enabled: ready,
  });
  const damageReportId = meta.data?.damageReportId;

  const report = useQuery({
    queryKey: ['damage-report', damageReportId],
    queryFn: () => damageApi.get(damageReportId!),
    enabled: ready && !!damageReportId,
  });

  const pairs = report.data?.pairs ?? [];
  const photos = report.data?.photos ?? [];
  const assetIds = useMemo(() => Array.from(new Set(pairs.map((p) => p.assetId))), [pairs]);

  // Resolve the paired ASSET photos so each incident photo shows the documented
  // asset it was matched to (side-by-side evidence).
  const assets = useQuery({
    queryKey: ['dossier-assets', dossierId, assetIds],
    queryFn: async () => {
      const list = await Promise.all(assetIds.map((aid) => propertiesApi.getAsset(aid).catch(() => null)));
      const map: Record<string, Asset> = {};
      list.forEach((a) => { if (a) map[a.id] = a; });
      return map;
    },
    enabled: ready && assetIds.length > 0,
  });

  const pairByPhoto = useMemo(() => {
    const m: Record<string, Pair> = {};
    pairs.forEach((p) => { m[p.damagePhotoId] = p; });
    return m;
  }, [pairs]);

  const pairedPhotos = photos.filter((p) => pairByPhoto[p.id]);
  const unpairedPhotos = photos.filter((p) => !pairByPhoto[p.id]);

  const pay = async () => {
    setPaying(true);
    try {
      const handle = await damageApi.dossierPay(dossierId);
      const result = await runCheckout({
        reference: handle.payment?.reference ?? '',
        authorizationUrl: handle.payment?.authorizationUrl ?? '',
      });
      if (result === 'failed') Alert.alert('Payment failed', 'Please try again.');
      else if (result === 'pending') Alert.alert('Payment not completed', 'No charge was made. You can tap Pay again any time.');
      qc.invalidateQueries({ queryKey: ['dossier-status', dossierId] });
    } catch (e) {
      Alert.alert('Could not start payment', isApiError(e) ? e.message : 'Please try again.');
    } finally {
      setPaying(false);
    }
  };

  const download = async () => {
    setDownloading(true);
    try {
      const { downloadUrl, fileName } = await damageApi.dossierDownload(dossierId);
      const url = resolveMediaUrl(downloadUrl);
      if (!url) throw new Error('No download URL');
      const name = /\.pdf$/i.test(fileName ?? '') ? fileName : `${fileName || `dossier-${dossierId}`}.pdf`;
      const dest = new File(Paths.cache, name);
      if (dest.exists) dest.delete();
      const file = await File.downloadFileAsync(url, dest);
      if (await Sharing.isAvailableAsync()) {
        await Sharing.shareAsync(file.uri, { mimeType: 'application/pdf', dialogTitle: name, UTI: 'com.adobe.pdf' });
      } else {
        await WebBrowser.openBrowserAsync(url);
      }
    } catch (e) {
      if (isApiError(e) && e.code === 'PAYMENT_REQUIRED') Alert.alert('Payment required', 'Complete payment to download.');
      else Alert.alert('Could not download', isApiError(e) ? e.message : 'Please try again.');
    } finally {
      setDownloading(false);
    }
  };

  // Open the FULL generated dossier (the multi-page PDF: cover, descriptions,
  // before/after, signatures) inside the app — an in-app browser tab, so the
  // owner can read the whole document without it being shared out or leaving
  // the app. (The evidence above is only a preview.)
  const viewDossier = async () => {
    setViewing(true);
    try {
      const { downloadUrl } = await damageApi.dossierDownload(dossierId); // fresh signed URL
      const url = resolveMediaUrl(downloadUrl);
      if (!url) throw new Error('No dossier URL');
      // Android's in-app browser DOWNLOADS a PDF URL instead of rendering it.
      // Route through Google's inline viewer so the pages render on screen,
      // inside the app. (Google fetches the public signed URL server-side.)
      const inline = `https://docs.google.com/viewer?embedded=true&url=${encodeURIComponent(url)}`;
      await WebBrowser.openBrowserAsync(inline, {
        toolbarColor: colors.primary,
        controlsColor: colors.onPrimary,
        enableBarCollapsing: true,
      });
    } catch (e) {
      if (isApiError(e) && e.code === 'PAYMENT_REQUIRED') Alert.alert('Payment required', 'Complete payment to view the dossier.');
      else Alert.alert('Could not open dossier', isApiError(e) ? e.message : 'Please try again.');
    } finally {
      setViewing(false);
    }
  };

  const rotate = useMutation({
    mutationFn: () => damageApi.rotateShareToken(dossierId),
    onSuccess: () => show('Share link rotated — old links no longer work'),
    onError: (e) => Alert.alert('Could not rotate', isApiError(e) ? e.message : 'Try again.'),
  });

  const retry = useMutation({
    mutationFn: () => damageApi.retryGeneration(dossierId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['dossier-status', dossierId] }),
    onError: (e) => Alert.alert('Could not retry', isApiError(e) ? e.message : 'Try again.'),
  });

  if (q.isLoading) return <Loading label="Checking dossier…" />;
  if (q.isError || !status) return <ErrorState onRetry={() => q.refetch()} />;

  const disaster = DISASTER_META[meta.data?.disasterType ?? report.data?.disasterType ?? 'OTHER'] ?? DISASTER_META.OTHER;
  const totalLoss = q.data?.totalEstimatedLoss ?? meta.data?.totalEstimatedLoss ?? report.data?.totalEstimatedLoss;

  return (
    <Screen>
      <Header title="Dossier" />

      {ready ? (
        <ReadyCover
          icon={disaster.icon}
          disasterLabel={disaster.label}
          propertyName={meta.data?.propertyName ?? report.data?.propertyName}
          occurredAt={report.data?.occurredAt}
          generatedAt={q.data?.generatedAt}
          totalLoss={totalLoss}
        />
      ) : (
        <StatusCard status={status} />
      )}

      {status === 'PENDING_PAYMENT' ? <Button title="Pay & generate dossier" loading={paying} onPress={pay} /> : null}

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

      {ready ? (
        <>
          {/* Cryptographic seal */}
          <Card style={{ backgroundColor: colors.tealTint }}>
            <View style={{ flexDirection: 'row', gap: spacing.sm, alignItems: 'flex-start' }}>
              <Ionicons name="shield-checkmark" size={18} color={colors.primary} />
              <View style={{ flex: 1, gap: 4 }}>
                <Text variant="labelMd" weight="semibold" color={colors.primary}>
                  Cryptographically sealed
                </Text>
                {q.data?.manifestHash ? (
                  <Text variant="labelMd" color={colors.primary} style={{ fontFamily: 'monospace' }}>
                    {q.data.manifestHash.slice(0, 24)}…
                  </Text>
                ) : null}
                <Text variant="labelMd" color={colors.primary}>
                  {[
                    photos.length ? `${photos.length} photo${photos.length === 1 ? '' : 's'}` : '',
                    pairs.length ? `${pairs.length} paired` : '',
                    q.data?.pageCount ? `${q.data.pageCount} pages` : '',
                  ].filter(Boolean).join(' · ') || 'Sealed into the PDF — any change breaks the signature.'}
                </Text>
              </View>
            </View>
          </Card>

          {/* Small yellow link to the FULL generated dossier, right by the evidence. */}
          <Pressable onPress={viewDossier} disabled={viewing} accessibilityRole="link" style={{ flexDirection: 'row', alignItems: 'center', gap: 6, alignSelf: 'flex-start' }}>
            <Ionicons name="document-text" size={15} color={colors.cta} />
            <Text variant="labelMd" weight="semibold" color={colors.cta} style={{ textDecorationLine: 'underline' }}>
              {viewing ? 'Opening dossier…' : 'View full dossier'}
            </Text>
          </Pressable>

          {/* In-app evidence viewer */}
          {report.isLoading ? (
            <Card>
              <View style={{ flexDirection: 'row', gap: spacing.md, alignItems: 'center' }}>
                <Loading />
                <Text variant="bodyMd" color={colors.textMuted}>Loading evidence…</Text>
              </View>
            </Card>
          ) : (
            <>
              {pairedPhotos.length ? (
                <View style={{ gap: spacing.md }}>
                  <SectionHeader title="Paired evidence" />
                  {pairedPhotos.map((photo) => (
                    <PairCard key={photo.id} photo={photo} pair={pairByPhoto[photo.id]} asset={assets.data?.[pairByPhoto[photo.id].assetId]} />
                  ))}
                </View>
              ) : null}

              {unpairedPhotos.length ? (
                <View style={{ gap: spacing.md }}>
                  <SectionHeader title={pairedPhotos.length ? 'Other damage photos' : 'Damage photos'} />
                  {unpairedPhotos.map((photo) => (
                    <IncidentCard key={photo.id} photo={photo} />
                  ))}
                </View>
              ) : null}
            </>
          )}

          {/* Actions */}
          <View style={{ gap: spacing.md }}>
            <Button title="View dossier" loading={viewing} onPress={viewDossier} leftIcon={<Ionicons name="document-text" size={18} color={colors.onCta} />} />
            <Button title="Download dossier (PDF)" variant="secondary" loading={downloading} onPress={download} leftIcon={<Ionicons name="download" size={18} color={colors.primary} />} />
            <Button title="Share PDF (WhatsApp & more)" variant="secondary" loading={downloading} onPress={download} leftIcon={<Ionicons name="logo-whatsapp" size={18} color={colors.primary} />} />
            <Button title="Share with an agent" variant="secondary" onPress={() => router.push(`/(app)/dossier/${dossierId}/share` as never)} />
            <Button title="Rotate share link" variant="ghost" loading={rotate.isPending} onPress={() => rotate.mutate()} />
          </View>
        </>
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

/** Rich READY cover: disaster, property, dates and total documented loss. */
function ReadyCover({
  icon,
  disasterLabel,
  propertyName,
  occurredAt,
  generatedAt,
  totalLoss,
}: {
  icon: keyof typeof Ionicons.glyphMap;
  disasterLabel: string;
  propertyName?: string;
  occurredAt?: string;
  generatedAt?: string;
  totalLoss?: number;
}) {
  return (
    <Hero>
      <View style={{ gap: spacing.md }}>
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: spacing.md }}>
          <View style={{ width: 44, height: 44, borderRadius: 22, backgroundColor: 'rgba(255,255,255,0.15)', alignItems: 'center', justifyContent: 'center' }}>
            <Ionicons name={icon} size={24} color={colors.cta} />
          </View>
          <View style={{ flex: 1 }}>
            <Text variant="labelMd" color={colors.tealMuted}>
              {disasterLabel} claim{occurredAt ? ` · ${shortDate(occurredAt)}` : ''}
            </Text>
            <Text variant="headlineSm" color={colors.onPrimary}>
              {propertyName ?? 'Damage dossier'}
            </Text>
          </View>
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: 4, backgroundColor: 'rgba(255,255,255,0.15)', paddingHorizontal: spacing.sm, paddingVertical: 4, borderRadius: radius.pill }}>
            <Ionicons name="shield-checkmark" size={13} color={colors.cta} />
            <Text variant="labelMd" color={colors.onPrimary}>Signed</Text>
          </View>
        </View>
        <View style={{ borderTopWidth: 1, borderTopColor: 'rgba(255,255,255,0.15)', paddingTop: spacing.md, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-end' }}>
          <View>
            <Text variant="labelMd" color={colors.tealMuted}>Documented loss</Text>
            <Text variant="headlineMd" color={colors.onPrimary}>{cedis(totalLoss)}</Text>
          </View>
          {generatedAt ? (
            <Text variant="labelMd" color={colors.tealMuted}>Generated {shortDate(generatedAt)}</Text>
          ) : null}
        </View>
      </View>
    </Hero>
  );
}

/** Damage photo matched to a documented asset — side-by-side evidence. */
function PairCard({ photo, pair, asset }: { photo: DamagePhoto; pair: Pair; asset?: Asset }) {
  const method = pair.pairingMethod === 'GPS_AUTO'
    ? `GPS matched${pair.distanceMeters != null ? ` · ${Math.round(pair.distanceMeters)}m` : ''}`
    : 'Manually paired';
  return (
    <Card style={{ gap: spacing.sm }}>
      <View style={{ flexDirection: 'row', gap: spacing.sm, alignItems: 'center' }}>
        <View style={{ flex: 1, gap: 4 }}>
          <Text variant="labelMd" color={colors.error} weight="semibold">Damage</Text>
          <EvidencePhotoBox uri={photo.photoUrl} variant="incident" gpsLat={photo.gpsLat} gpsLng={photo.gpsLng} capturedAt={photo.capturedAt} verified={photo.sha256Hash} />
        </View>
        <Ionicons name="link" size={18} color={colors.textMuted} />
        <View style={{ flex: 1, gap: 4 }}>
          <Text variant="labelMd" color={colors.primary} weight="semibold">Documented asset</Text>
          <EvidencePhotoBox uri={asset?.photoUrl} variant="vault" verified={asset?.sha256Hash} />
        </View>
      </View>
      {asset?.description ? (
        <Text variant="bodyMd" numberOfLines={1}>{asset.description}{asset.estimatedValue != null ? ` · ${cedis(asset.estimatedValue)}` : ''}</Text>
      ) : null}
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: 4, alignSelf: 'flex-start', backgroundColor: colors.tealTint, paddingHorizontal: spacing.sm, paddingVertical: 4, borderRadius: radius.pill }}>
        <Ionicons name={pair.pairingMethod === 'GPS_AUTO' ? 'navigate' : 'hand-left'} size={12} color={colors.primary} />
        <Text variant="labelMd" color={colors.primary}>{method}</Text>
      </View>
    </Card>
  );
}

/** An unpaired damage photo shown on its own. */
function IncidentCard({ photo }: { photo: DamagePhoto }) {
  return (
    <Card style={{ gap: spacing.sm }}>
      <EvidencePhotoBox uri={photo.photoUrl} variant="incident" gpsLat={photo.gpsLat} gpsLng={photo.gpsLng} capturedAt={photo.capturedAt} verified={photo.sha256Hash} height={200} />
      {photo.description ? <Text variant="bodyMd" numberOfLines={2}>{photo.description}</Text> : null}
    </Card>
  );
}

function EvidencePhotoBox(props: EvidencePhotoProps) {
  return <EvidencePhoto height={130} radiusSize={radius.md} {...props} />;
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
          <Text variant="labelMd" color={colors.tealMuted}>Dossier status</Text>
          <Text variant="headlineSm" color={colors.onPrimary}>{m.label}</Text>
        </View>
      </View>
    </Hero>
  );
}
