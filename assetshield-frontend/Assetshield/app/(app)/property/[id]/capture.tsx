import { Ionicons } from '@expo/vector-icons';
import { useQueryClient } from '@tanstack/react-query';
import { router, useLocalSearchParams } from 'expo-router';
import { useState } from 'react';
import { Alert, Pressable, ScrollView, View } from 'react-native';
import { AssetCategory, isApiError } from '@/lib/api';
import { CapturedImage, getLocationFix, LocationFix, PermissionError, pickImage } from '@/lib/media/capture';
import { uploadAssetPhoto } from '@/lib/media/uploads';
import { useOffline } from '@/lib/offline/OfflineProvider';
import { Button, Card, EvidencePhoto, Header, Input, LocationConfirm, Screen, Text } from '@/components/ui';
import { colors, radius, spacing } from '@/theme';

const CATEGORIES: AssetCategory[] = ['ELECTRONICS', 'FURNITURE', 'CLOTHING_STOCK', 'MACHINERY', 'DOCUMENTS', 'OTHER'];

/** DOCUMENT (beat 1): capture an asset → hash exact bytes → upload (or queue). */
export default function CaptureAsset() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const propertyId = id!;
  const qc = useQueryClient();
  const { refreshPending } = useOffline();

  const [image, setImage] = useState<CapturedImage | null>(null);
  const [description, setDescription] = useState('');
  const [value, setValue] = useState('');
  const [category, setCategory] = useState<AssetCategory>('ELECTRONICS');
  const [loading, setLoading] = useState(false);
  const [fix, setFix] = useState<LocationFix | undefined>(undefined);

  const refreshFix = () => {
    setFix(undefined); // shows the "getting a fix" state
    getLocationFix().then(setFix);
  };

  const choose = async (source: 'camera' | 'library') => {
    try {
      const img = await pickImage(source);
      if (img) {
        setImage(img);
        refreshFix(); // grab the fix now so the user can confirm it before upload
      }
    } catch (e) {
      if (e instanceof PermissionError) {
        Alert.alert('Permission needed', `Please allow ${e.kind} access in settings to capture assets.`);
      }
    }
  };

  const submit = async () => {
    if (!image) return;
    setLoading(true);
    try {
      const estimatedValue = value ? Number(value.replace(/[^0-9.]/g, '')) : undefined;
      const outcome = await uploadAssetPhoto(propertyId, image, {
        description,
        estimatedValue,
        category,
        coords: fix ? { gpsLat: fix.gpsLat, gpsLng: fix.gpsLng } : undefined,
      });
      await refreshPending();
      qc.invalidateQueries({ queryKey: ['assets', propertyId] });
      qc.invalidateQueries({ queryKey: ['property', propertyId] });

      if (outcome.status === 'duplicate') {
        Alert.alert('Already documented', 'This exact photo is already on this property.', [{ text: 'OK', onPress: () => router.back() }]);
      } else if (outcome.status === 'queued') {
        Alert.alert('Saved offline', 'This asset will sync automatically when you’re back online.', [{ text: 'OK', onPress: () => router.back() }]);
      } else {
        router.back();
      }
    } catch (e) {
      if (isApiError(e)) {
        if (e.code === 'HASH_MISMATCH') Alert.alert('Verification failed', 'Please retake the photo and try again.');
        else if (e.code === 'FREE_TIER_LIMIT') Alert.alert('Upgrade required', e.message);
        else Alert.alert('Upload failed', e.message);
      } else {
        Alert.alert('Upload failed', 'Please try again.');
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <Screen footer={<Button title={image ? 'Save asset' : 'Take a photo first'} loading={loading} disabled={!image || !description.trim()} onPress={submit} />}>
      <Header title="Capture asset" />

      {image ? (
        <Card padded={false} style={{ overflow: 'hidden' }}>
          <EvidencePhoto
            localUri={image.uri}
            height={240}
            gpsLat={fix?.gpsLat}
            gpsLng={fix?.gpsLng}
            capturedAt={new Date().toISOString()}
          />
          <Pressable onPress={() => choose('camera')} style={{ position: 'absolute', top: spacing.md, right: spacing.md, backgroundColor: colors.card, borderRadius: radius.pill, padding: spacing.sm }}>
            <Ionicons name="camera-reverse" size={22} color={colors.primary} />
          </Pressable>
        </Card>
      ) : (
        <View style={{ flexDirection: 'row', gap: spacing.md }}>
          <CaptureTile icon="camera" label="Take photo" onPress={() => choose('camera')} />
          <CaptureTile icon="images" label="From gallery" onPress={() => choose('library')} />
        </View>
      )}

      {image ? <LocationConfirm fix={fix} onRefresh={refreshFix} /> : null}

      <View style={{ gap: spacing.lg }}>
        <Input label="Description" value={description} onChangeText={setDescription} placeholder="e.g. Brother sewing machine" />
        <Input label="Estimated value (₵)" value={value} onChangeText={setValue} placeholder="1500" keyboardType="numeric" />

        <View style={{ gap: spacing.xs }}>
          <Text variant="labelMd" color={colors.textMuted}>
            Category
          </Text>
          <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ gap: spacing.sm }}>
            {CATEGORIES.map((c) => {
              const active = c === category;
              return (
                <Pressable
                  key={c}
                  onPress={() => setCategory(c)}
                  style={{
                    paddingHorizontal: spacing.lg,
                    paddingVertical: spacing.sm,
                    borderRadius: radius.pill,
                    backgroundColor: active ? colors.primary : colors.card,
                    borderWidth: 1,
                    borderColor: active ? colors.primary : colors.border,
                  }}
                >
                  <Text variant="labelMd" color={active ? colors.onPrimary : colors.textMuted}>
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
              We compute a tamper-evident fingerprint (SHA-256) of this exact photo before uploading.
            </Text>
          </View>
        </Card>
      </View>
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
