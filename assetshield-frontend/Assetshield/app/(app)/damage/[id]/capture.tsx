import { Ionicons } from '@expo/vector-icons';
import { useQueryClient } from '@tanstack/react-query';
import { Image } from 'expo-image';
import { router, useLocalSearchParams } from 'expo-router';
import { useState } from 'react';
import { Alert, Pressable, View } from 'react-native';
import { damageApi, DamagePhoto, isApiError, PairingSuggestion } from '@/lib/api';
import { CapturedImage, getLocationFix, LocationFix, PermissionError, pickImage } from '@/lib/media/capture';
import { uploadDamagePhoto } from '@/lib/media/uploads';
import { useOffline } from '@/lib/offline/OfflineProvider';
import { Button, Card, EmptyState, Header, Input, LocationConfirm, RemoteImage, Screen, Text } from '@/components/ui';
import { colors, radius, spacing } from '@/theme';

type Phase = 'capture' | 'pairing';

/** DISASTER (beat 2): capture a damage photo → upload → pair with a documented asset. */
export default function CaptureDamage() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const reportId = id!;
  const qc = useQueryClient();
  const { refreshPending } = useOffline();

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
      if (e instanceof PermissionError) Alert.alert('Permission needed', `Allow ${e.kind} access to capture damage.`);
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
        Alert.alert('Already added', 'This exact photo is already on the report.', [{ text: 'OK', onPress: () => router.back() }]);
      } else if (outcome.status === 'queued') {
        Alert.alert('Saved offline', 'This photo will sync when you’re back online.', [{ text: 'OK', onPress: () => router.back() }]);
      } else {
        setPhoto(outcome.data.photo);
        setSuggestions(outcome.data.pairingSuggestions ?? []);
        setPhase('pairing');
      }
    } catch (e) {
      if (isApiError(e) && e.code === 'HASH_MISMATCH') Alert.alert('Verification failed', 'Please retake and try again.');
      else Alert.alert('Upload failed', isApiError(e) ? e.message : 'Try again.');
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
      else Alert.alert('Could not pair', isApiError(e) ? e.message : 'Try again.');
    } finally {
      setPairingId(null);
    }
  };

  if (phase === 'pairing') {
    return (
      <Screen footer={<Button title="Skip pairing" variant="secondary" onPress={() => router.back()} />}>
        <Header title="Pair with an asset" showBack={false} />
        <Card padded={false} style={{ overflow: 'hidden' }}>
          <RemoteImage uri={photo?.photoUrl} height={180} />
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
      {image ? (
        <Card padded={false} style={{ overflow: 'hidden' }}>
          <Image source={{ uri: image.uri }} style={{ width: '100%', height: 240 }} contentFit="cover" />
        </Card>
      ) : (
        <View style={{ flexDirection: 'row', gap: spacing.md }}>
          <CaptureTile icon="camera" label="Take photo" onPress={() => choose('camera')} />
          <CaptureTile icon="images" label="From gallery" onPress={() => choose('library')} />
        </View>
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
