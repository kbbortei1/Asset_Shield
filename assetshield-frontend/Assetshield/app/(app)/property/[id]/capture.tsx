import { Ionicons } from '@expo/vector-icons';
import { useQueryClient } from '@tanstack/react-query';
import { router, useLocalSearchParams } from 'expo-router';
import { useRef, useState } from 'react';
import { Alert, Image, Pressable, ScrollView, View } from 'react-native';
import { AssetCategory, isApiError } from '@/lib/api';
import { CapturedImage, getLocationFix, LocationFix, PermissionError, pickImage } from '@/lib/media/capture';
import { uploadAssetMulti } from '@/lib/media/uploads';
import { useOffline } from '@/lib/offline/OfflineProvider';
import { Button, Card, EvidencePhoto, Header, Input, LocationConfirm, Screen, Text, useToast } from '@/components/ui';
import { colors, radius, spacing } from '@/theme';

const CATEGORIES: AssetCategory[] = ['ELECTRONICS', 'FURNITURE', 'CLOTHING_STOCK', 'MACHINERY', 'DOCUMENTS', 'OTHER'];
const MAX_PHOTOS = 15;

/** One staged photo — its own image, GPS fix and capture time. */
type Photo = {
  id: number;
  image: CapturedImage;
  capturedAt: string;
  fix?: LocationFix;
  fixLoading: boolean;
};

/**
 * DOCUMENT (beat 1): capture ONE asset (e.g. "Kitchen") from 1..15 photos.
 * Description, category and value are entered ONCE and shared by every photo;
 * each photo keeps its own GPS + hash. Camera-only by design — gallery uploads
 * would let people submit photos they didn't take, defeating tamper-evidence.
 */
export default function CaptureAsset() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const propertyId = id!;
  const qc = useQueryClient();
  const { refreshPending } = useOffline();
  const { show } = useToast();

  const [photos, setPhotos] = useState<Photo[]>([]);
  const [activeId, setActiveId] = useState<number | null>(null);
  const [description, setDescription] = useState('');
  const [value, setValue] = useState('');
  const [category, setCategory] = useState<AssetCategory>('ELECTRONICS');
  const [saving, setSaving] = useState(false);
  const nextId = useRef(1);

  const active = photos.find((p) => p.id === activeId) ?? null;

  const patch = (pid: number, p: Partial<Photo>) =>
    setPhotos((prev) => prev.map((it) => (it.id === pid ? { ...it, ...p } : it)));

  const grabFix = (pid: number) => {
    patch(pid, { fix: undefined, fixLoading: true });
    getLocationFix().then((fix) => patch(pid, { fix, fixLoading: false }));
  };

  const addPhoto = async () => {
    if (photos.length >= MAX_PHOTOS) {
      show(`You can add up to ${MAX_PHOTOS} photos`);
      return;
    }
    try {
      const img = await pickImage('camera');
      if (!img) return;
      const pid = nextId.current++;
      setPhotos((prev) => [...prev, { id: pid, image: img, capturedAt: new Date().toISOString(), fixLoading: true }]);
      setActiveId(pid);
      grabFix(pid);
    } catch (e) {
      if (e instanceof PermissionError) {
        Alert.alert('Permission needed', 'Please allow camera access in settings to capture assets.');
      }
    }
  };

  const retake = async (pid: number) => {
    try {
      const img = await pickImage('camera');
      if (!img) return;
      patch(pid, { image: img, capturedAt: new Date().toISOString() });
      grabFix(pid);
    } catch (e) {
      if (e instanceof PermissionError) Alert.alert('Permission needed', 'Please allow camera access in settings.');
    }
  };

  const removePhoto = (pid: number) => {
    setPhotos((prev) => {
      const next = prev.filter((it) => it.id !== pid);
      if (activeId === pid) setActiveId(next.length ? next[next.length - 1].id : null);
      return next;
    });
  };

  const save = async () => {
    if (!photos.length || !description.trim()) return;
    setSaving(true);
    try {
      const estimatedValue = value ? Number(value.replace(/[^0-9.]/g, '')) : undefined;
      const outcome = await uploadAssetMulti(
        propertyId,
        photos.map((p) => ({
          image: p.image,
          coords: p.fix ? { gpsLat: p.fix.gpsLat, gpsLng: p.fix.gpsLng } : undefined,
          capturedAt: p.capturedAt,
        })),
        { description: description.trim(), estimatedValue, category },
      );

      if (outcome.status === 'needs-online') {
        Alert.alert('Connection needed', 'Saving multiple photos as one asset needs an internet connection. Reconnect and try again.');
        return;
      }
      await refreshPending();
      qc.invalidateQueries({ queryKey: ['assets', propertyId] });
      qc.invalidateQueries({ queryKey: ['property', propertyId] });

      if (outcome.status === 'uploaded' && outcome.data?.duplicateWarning) {
        Alert.alert(
          'Duplicate photo detected',
          'One of these photos already documents an asset on another property. It was saved, but duplicate evidence can be rejected by insurers.',
          [{ text: 'Understood', onPress: () => router.back() }],
        );
      } else {
        show(
          outcome.status === 'queued'
            ? 'Saved offline — will sync automatically'
            : outcome.status === 'duplicate'
              ? 'Already documented on this property'
              : 'Asset saved',
        );
        router.back();
      }
    } catch (e) {
      if (isApiError(e)) {
        if (e.code === 'HASH_MISMATCH') Alert.alert('Verification failed', 'Please retake the photo and try again.');
        else if (e.code === 'FREE_TIER_LIMIT') Alert.alert('Upgrade required', e.message);
        else if (e.code === 'DUPLICATE_ASSET_HASH') Alert.alert('Duplicate photo', e.message);
        else Alert.alert('Upload failed', e.message);
      } else {
        Alert.alert('Upload failed', 'Please try again.');
      }
    } finally {
      setSaving(false);
    }
  };

  const footerTitle = saving
    ? 'Saving…'
    : photos.length === 0
      ? 'Take a photo first'
      : !description.trim()
        ? 'Add a description to save'
        : `Save asset (${photos.length} photo${photos.length === 1 ? '' : 's'})`;

  return (
    <Screen
      footer={<Button title={footerTitle} loading={saving} disabled={saving || photos.length === 0 || !description.trim()} onPress={save} />}
    >
      <Header title="Capture asset" />

      {photos.length === 0 ? (
        <CaptureTile icon="camera" label="Take photo" onPress={addPhoto} />
      ) : (
        <>
          {/* WhatsApp-style filmstrip: every photo of this one asset. */}
          <View style={{ gap: spacing.xs }}>
            <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ gap: spacing.sm, paddingVertical: spacing.xs, paddingHorizontal: 2 }}>
              {photos.map((p) => (
                <Thumb
                  key={p.id}
                  uri={p.image.uri}
                  active={p.id === activeId}
                  noGps={!p.fixLoading && !(p.fix?.gpsLat != null)}
                  onPress={() => setActiveId(p.id)}
                  onRemove={() => removePhoto(p.id)}
                />
              ))}
              {photos.length < MAX_PHOTOS ? <AddThumb onPress={addPhoto} /> : null}
            </ScrollView>
            <Text variant="labelMd" color={colors.textMuted}>
              {photos.length} of {MAX_PHOTOS} photos · one asset
            </Text>
          </View>

          {active ? (
            <>
              <Card padded={false} style={{ overflow: 'hidden' }}>
                <EvidencePhoto localUri={active.image.uri} height={240} gpsLat={active.fix?.gpsLat} gpsLng={active.fix?.gpsLng} capturedAt={active.capturedAt} />
                <Pressable accessibilityRole="button" accessibilityLabel="Retake photo" onPress={() => retake(active.id)} style={{ position: 'absolute', top: spacing.md, right: spacing.md, backgroundColor: colors.card, borderRadius: radius.pill, padding: spacing.sm }}>
                  <Ionicons name="camera-reverse" size={22} color={colors.primary} />
                </Pressable>
              </Card>
              <LocationConfirm fix={active.fix} onRefresh={() => grabFix(active.id)} />
            </>
          ) : null}

          {/* Shared details — one description/value/category for the whole asset. */}
          <View style={{ gap: spacing.lg }}>
            <Input label="Description" value={description} onChangeText={setDescription} placeholder="e.g. Kitchen" />
            <Input label="Estimated value (₵) — optional" value={value} onChangeText={setValue} placeholder="1500" keyboardType="numeric" />

            <View style={{ gap: spacing.xs }}>
              <Text variant="labelMd" color={colors.textMuted}>Category</Text>
              <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ gap: spacing.sm }}>
                {CATEGORIES.map((c) => {
                  const isActive = c === category;
                  return (
                    <Pressable
                      key={c}
                      onPress={() => setCategory(c)}
                      style={{
                        paddingHorizontal: spacing.lg,
                        paddingVertical: spacing.sm,
                        borderRadius: radius.pill,
                        backgroundColor: isActive ? colors.primary : colors.card,
                        borderWidth: 1,
                        borderColor: isActive ? colors.primary : colors.border,
                      }}
                    >
                      <Text variant="labelMd" color={isActive ? colors.onPrimary : colors.textMuted}>
                        {c.split('_').map((w) => w[0] + w.slice(1).toLowerCase()).join(' ')}
                      </Text>
                    </Pressable>
                  );
                })}
              </ScrollView>
            </View>

            <Card style={{ backgroundColor: colors.tealTint }}>
              <View style={{ flexDirection: 'row', gap: spacing.sm }}>
                <Ionicons name="shield-checkmark" size={18} color={colors.primary} />
                <Text variant="labelMd" color={colors.primary} style={{ flex: 1 }}>
                  We compute a tamper-evident fingerprint (SHA-256) of each exact photo before uploading.
                </Text>
              </View>
            </Card>
          </View>
        </>
      )}
    </Screen>
  );
}

function Thumb({ uri, active, noGps, onPress, onRemove }: { uri: string; active: boolean; noGps: boolean; onPress: () => void; onRemove: () => void }) {
  return (
    <Pressable onPress={onPress} accessibilityRole="button" style={{ paddingTop: 6, paddingRight: 6 }}>
      <View style={{ width: 64, height: 64, borderRadius: radius.md, overflow: 'hidden', borderWidth: active ? 2 : 1, borderColor: active ? colors.primary : colors.border, backgroundColor: colors.card }}>
        <Image source={{ uri }} style={{ width: '100%', height: '100%' }} resizeMode="cover" />
        {/* amber dot = this photo has no GPS fix (still fine to save; just a hint) */}
        {noGps ? (
          <View style={{ position: 'absolute', left: 4, bottom: 4, width: 10, height: 10, borderRadius: 5, backgroundColor: colors.warning, borderWidth: 1, borderColor: colors.white }} />
        ) : null}
      </View>
      <Pressable onPress={onRemove} accessibilityRole="button" accessibilityLabel="Remove photo" hitSlop={8} style={{ position: 'absolute', top: 0, right: 0, width: 20, height: 20, borderRadius: 10, backgroundColor: colors.text, alignItems: 'center', justifyContent: 'center', borderWidth: 2, borderColor: colors.card }}>
        <Ionicons name="close" size={11} color={colors.white} />
      </Pressable>
    </Pressable>
  );
}

function AddThumb({ onPress }: { onPress: () => void }) {
  return (
    <Pressable onPress={onPress} accessibilityRole="button" accessibilityLabel="Add another photo" style={{ paddingTop: 6 }}>
      <View style={{ width: 64, height: 64, borderRadius: radius.md, borderWidth: 1, borderStyle: 'dashed', borderColor: colors.primary, alignItems: 'center', justifyContent: 'center', gap: 2, backgroundColor: colors.tealTint }}>
        <Ionicons name="camera" size={20} color={colors.primary} />
        <Text variant="labelMd" color={colors.primary}>Add</Text>
      </View>
    </Pressable>
  );
}

function CaptureTile({ icon, label, onPress }: { icon: keyof typeof Ionicons.glyphMap; label: string; onPress: () => void }) {
  return (
    <Pressable onPress={onPress} style={{ flex: 1 }}>
      <Card style={{ alignItems: 'center', paddingVertical: spacing.xl, gap: spacing.sm, borderWidth: 1, borderColor: colors.border }}>
        <Ionicons name={icon} size={32} color={colors.primary} />
        <Text variant="labelMd" color={colors.textMuted}>{label}</Text>
      </Card>
    </Pressable>
  );
}
