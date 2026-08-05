import { Ionicons } from '@expo/vector-icons';
import { useQueryClient } from '@tanstack/react-query';
import { router, useLocalSearchParams } from 'expo-router';
import { useState } from 'react';
import { Pressable, View } from 'react-native';

import { damageApi, DamagePhoto, isApiError, PairingSuggestion } from '@/lib/api';
import { CapturedImage, getLocationFix, LocationFix, PermissionError, pickImage } from '@/lib/media/capture';
import { uploadDamagePhoto } from '@/lib/media/uploads';
import { useOffline } from '@/lib/offline/OfflineProvider';
import { Button, Card, EmptyState, EvidencePhoto, Header, Input, LocationConfirm, RemoteImage, Screen, Text, useToast, showAlert } from '@/components/ui';
import { colors, radius, spacing } from '@/theme';

type Phase = 'capture' | 'pairing';

/** DISASTER (beat 2): capture a damage photo → upload → pair with a documented asset. */
export default function CaptureDamage() {
  const { id, assetId, assetName } = useLocalSearchParams<{ id: string; assetId?: string; assetName?: string }>();
  const reportId = id!;
  const qc = useQueryClient();
  const { refreshPending } = useOffline();
  const { show } = useToast();

  const [phase, setPhase] = useState<Phase>('capture');
  const [image, setImage] = useState<CapturedImage | null>(null);
  const [description, setDescription] = useState('');
  const [loading, setLoading] = useState(false);
  const [photo, setPhoto] = useState<DamagePhoto | null>(null);
  const [suggestions, setSuggestions] = useState<PairingSuggestion[]>([]);
  const [pairingId, setPairingId] = useState<string | null>(null);
  const [fix, setFix] = useState<LocationFix | undefined>(undefined);

  const refreshFix = () => {
    setFix(undefined);
    getLocationFix().then(setFix);
  };

  const choose = async (source: 'camera' | 'library') => {
    try {
      const img = await pickImage(source);
      if (img) {
        setImage(img);
        refreshFix(); // pairing suggestions depend on this fix - let the user see it
      }
    } catch (e) {
      if (e instanceof PermissionError) showAlert('Permission needed', `Allow ${e.kind} access to capture damage.`);
    }
  };

  const upload = async () => {
    if (!image) return;
    setLoading(true);
    try {
      const outcome = await uploadDamagePhoto(reportId, image, {
        description,
        coords: fix ? { gpsLat: fix.gpsLat, gpsLng: fix.gpsLng } : undefined,
      });
      await refreshPending();
      qc.invalidateQueries({ queryKey: ['report', reportId] });

      if (outcome.status === 'duplicate') {
        show('This photo is already on the report');
        router.back();
      } else if (outcome.status === 'queued') {
        show('Saved offline — will sync automatically');
        router.back();
      } else {
        const uploaded = outcome.data.photo;
        // Asset-anchored: entered from a specific asset → link straight to it
        // (a deliberate MANUAL pair, no GPS guessing). Fall back to the
        // suggestion UI only if that pairing call fails.
        if (assetId) {
          try {
            await damageApi.pair(reportId, { damagePhotoId: uploaded.id, assetId, pairingMethod: 'MANUAL' });
            qc.invalidateQueries({ queryKey: ['report', reportId] });
            show(assetName ? `Damage linked to ${assetName}` : 'Damage linked to the asset');
            router.back();
            return;
          } catch {
            // pairing failed — don't lose the photo; show the suggestion UI
          }
        }
        setPhoto(uploaded);
        setSuggestions(outcome.data.pairingSuggestions ?? []);
        setPhase('pairing');
      }
    } catch (e) {
      if (isApiError(e) && e.code === 'HASH_MISMATCH') showAlert('Verification failed', 'Please retake and try again.');
      else showAlert('Upload failed', isApiError(e) ? e.message : 'Try again.');
    } finally {
      setLoading(false);
    }
  };

  const pair = async (assetId: string) => {
    if (!photo) return;
    setPairingId(assetId);
    try {
      await damageApi.pair(reportId, { damagePhotoId: photo.id, assetId, pairingMethod: 'GPS_AUTO' });
      qc.invalidateQueries({ queryKey: ['report', reportId] });
      router.back();
    } catch (e) {
      if (isApiError(e) && e.code === 'ALREADY_RESPONDED') router.back();
      else showAlert('Could not pair', isApiError(e) ? e.message : 'Try again.');
    } finally {
      setPairingId(null);
    }
  };

  if (phase === 'pairing') {
    return (
      <Screen footer={<Button title="Skip pairing" variant="secondary" onPress={() => router.back()} />}>
        <Header title="Pair with an asset" showBack={false} />
        <Card padded={false} style={{ overflow: 'hidden' }}>
          <EvidencePhoto
            uri={photo?.photoUrl}
            height={180}
            gpsLat={photo?.gpsLat}
            gpsLng={photo?.gpsLng}
            capturedAt={photo?.capturedAt}
            verified={photo?.sha256Hash}
            variant="incident"
          />
        </Card>
        <Text variant="bodyMd" color={colors.textMuted}>
          We found these documented assets near where this photo was taken. Pair to show before/after evidence.
        </Text>

        {suggestions.length === 0 ? (
          <EmptyState icon="git-compare-outline" title="No nearby matches" body="You can pair manually later from the report, or skip for now." />
        ) : (
          suggestions.map((s) => (
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
                <Button title="Pair" fullWidth={false} loading={pairingId === s.assetId} onPress={() => pair(s.assetId)} />
              </View>
            </Card>
          ))
        )}
      </Screen>
    );
  }

  return (
    <Screen footer={<Button title={image ? 'Upload photo' : 'Take a photo first'} loading={loading} disabled={!image} onPress={upload} />}>
      <Header title="Add damage photo" />
      {assetName ? (
        <Card style={{ backgroundColor: colors.tealTint }}>
          <View style={{ flexDirection: 'row', gap: spacing.sm, alignItems: 'center' }}>
            <Ionicons name="link" size={18} color={colors.primary} />
            <Text variant="labelMd" color={colors.primary} style={{ flex: 1 }}>
              Documenting damage to <Text variant="labelMd" weight="semibold" color={colors.primary}>{assetName}</Text> — this photo links to it automatically.
            </Text>
          </View>
        </Card>
      ) : null}
      {image ? (
        <Card padded={false} style={{ overflow: 'hidden' }}>
          <EvidencePhoto
            localUri={image.uri}
            height={240}
            gpsLat={fix?.gpsLat}
            gpsLng={fix?.gpsLng}
            capturedAt={new Date().toISOString()}
            variant="incident"
          />
        </Card>
      ) : (
        // Camera-only by design: damage evidence must be captured live, not
        // picked from the gallery (which could be edited/AI images).
        <CaptureTile icon="camera" label="Take photo" onPress={() => choose('camera')} />
      )}
      {image ? <LocationConfirm fix={fix} onRefresh={refreshFix} /> : null}
      <Input label="Description (optional)" value={description} onChangeText={setDescription} placeholder="e.g. Burnt stock" />
    </Screen>
  );
}

function CaptureTile({ icon, label, onPress }: { icon: keyof typeof Ionicons.glyphMap; label: string; onPress: () => void }) {
  return (
    <Pressable onPress={onPress} style={{ flex: 1 }}>
      <Card style={{ alignItems: 'center', paddingVertical: spacing.xl, gap: spacing.sm, borderWidth: 1, borderColor: colors.border }}>
        <Ionicons name={icon} size={32} color={colors.primary} />
        <Text variant="labelMd" color={colors.textMuted}>
          {label}
        </Text>
      </Card>
    </Pressable>
  );
}
