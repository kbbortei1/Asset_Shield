import { Ionicons } from '@expo/vector-icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { router, useLocalSearchParams } from 'expo-router';
import { useEffect, useState } from 'react';
import { Alert, Pressable, ScrollView, View } from 'react-native';
import { AssetPhoto, propertiesApi, usersApi } from '@/lib/api';
import { buildFileForm, pickImage } from '@/lib/media/capture';
import { Button, Card, EvidencePhoto, Header, Input, Loading, RemoteImage, Screen, Text, VerifiedBadge, useConfirm, useToast } from '@/components/ui';
import { colors, radius, spacing } from '@/theme';

export default function AssetDetail() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const assetId = id!;
  const qc = useQueryClient();
  const { show } = useToast();
  const confirm = useConfirm();
  const q = useQuery({ queryKey: ['asset', assetId], queryFn: () => propertiesApi.getAsset(assetId) });

  const [description, setDescription] = useState('');
  const [value, setValue] = useState('');
  const [warranty, setWarranty] = useState('');
  const [service, setService] = useState('');
  const [selIdx, setSelIdx] = useState(0); // which photo of the asset is shown

  useEffect(() => {
    if (q.data) {
      setDescription(q.data.description);
      setValue(q.data.estimatedValue != null ? String(q.data.estimatedValue) : '');
      setWarranty(q.data.warrantyExpiresOn ?? '');
      setService(q.data.nextServiceOn ?? '');
    }
  }, [q.data]);

  const save = useMutation({
    mutationFn: () => {
      for (const [label, date] of [['Warranty expiry', warranty], ['Next service', service]] as const) {
        if (date && !isIsoDate(date)) {
          throw new Error(`${label} must be a valid date in YYYY-MM-DD format.`);
        }
      }
      return propertiesApi.updateAsset(assetId, {
        description,
        estimatedValue: value ? Number(value) : undefined,
        warrantyExpiresOn: warranty || undefined,
        nextServiceOn: service || undefined,
      });
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['asset', assetId] });
      if (q.data?.propertyId) qc.invalidateQueries({ queryKey: ['assets', q.data.propertyId] });
      show('Asset updated');
    },
    onError: (e: any) => Alert.alert('Could not save', e?.message ?? 'Try again.'),
  });

  const addReceipt = async () => {
    const img = await pickImage('library').catch(() => null);
    if (!img) return;
    try {
      await propertiesApi.uploadReceipt(assetId, buildFileForm(img));
      qc.invalidateQueries({ queryKey: ['asset', assetId] });
      show('Receipt added');
    } catch (e: any) {
      Alert.alert('Could not upload', e?.message ?? 'Try again.');
    }
  };

  // Deleting evidence is irreversible → confirm, then re-auth with the password.
  const remove = async () => {
    let passwordError: string | undefined;
    for (;;) {
      const { confirmed, password } = await confirm({
        title: 'Delete asset?',
        message: 'This permanently removes the asset and its evidence from your records. This cannot be undone.',
        confirmLabel: 'Delete',
        destructive: true,
        icon: 'trash-outline',
        requirePassword: true,
        passwordError,
      });
      if (!confirmed) return;
      try {
        const ok = await usersApi.verifyPassword(password ?? '');
        if (!ok) {
          passwordError = 'Incorrect password. Please try again.';
          continue;
        }
      } catch {
        Alert.alert('Could not verify', 'Please check your connection and try again.');
        return;
      }
      const propertyId = q.data?.propertyId;
      try {
        await propertiesApi.removeAsset(assetId);
        if (propertyId) qc.invalidateQueries({ queryKey: ['assets', propertyId] });
        router.back();
      } catch (e: any) {
        Alert.alert('Could not delete', e?.message ?? 'Try again.');
      }
      return;
    }
  };

  if (q.isLoading) return <Loading />;
  const a = q.data!;

  // Gallery: all photos of the asset (cover first). Pre-multi-photo assets and
  // the flat list response have no photos[] → fall back to the cover fields.
  const gallery: AssetPhoto[] = a.photos?.length
    ? a.photos
    : [{ id: a.id, photoUrl: a.photoUrl, sha256Hash: a.sha256Hash, gpsLat: a.gpsLat, gpsLng: a.gpsLng, capturedAt: a.capturedAt }];
  const sel = gallery[Math.min(selIdx, gallery.length - 1)];

  return (
    <Screen>
      <Header title="Asset" />
      <Card padded={false} style={{ overflow: 'hidden' }}>
        <EvidencePhoto
          uri={sel.photoUrl}
          height={260}
          gpsLat={sel.gpsLat}
          gpsLng={sel.gpsLng}
          capturedAt={sel.capturedAt}
          verified={sel.sha256Hash}
          zoomable
        />
      </Card>

      {gallery.length > 1 ? (
        <View style={{ gap: spacing.xs }}>
          <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ gap: spacing.sm, paddingHorizontal: 2 }}>
            {gallery.map((p, i) => (
              <Pressable key={p.id} onPress={() => setSelIdx(i)} accessibilityRole="button">
                <View style={{ borderRadius: radius.md, overflow: 'hidden', borderWidth: i === selIdx ? 2 : 1, borderColor: i === selIdx ? colors.primary : colors.border }}>
                  <RemoteImage uri={p.photoUrl} width={60} height={60} radius={radius.md} />
                </View>
              </Pressable>
            ))}
          </ScrollView>
          <Text variant="labelMd" color={colors.textMuted}>
            Photo {Math.min(selIdx, gallery.length - 1) + 1} of {gallery.length}
          </Text>
        </View>
      ) : null}

      <View style={{ flexDirection: 'row', gap: spacing.sm, flexWrap: 'wrap' }}>
        <VerifiedBadge hash={sel.sha256Hash} />
        {sel.capturedAt ? (
          <Text variant="labelMd" color={colors.textMuted}>
            <Ionicons name="time-outline" size={12} /> {new Date(sel.capturedAt).toLocaleDateString()}
          </Text>
        ) : null}
        {sel.gpsLat && sel.gpsLng ? (
          <Text variant="labelMd" color={colors.textMuted}>
            <Ionicons name="location-outline" size={12} /> {sel.gpsLat.toFixed(4)}, {sel.gpsLng.toFixed(4)}
          </Text>
        ) : null}
      </View>

      <View style={{ gap: spacing.lg }}>
        <Input label="Description" value={description} onChangeText={setDescription} />
        <Input label="Estimated value (₵)" value={value} onChangeText={setValue} keyboardType="numeric" />
        <View style={{ flexDirection: 'row', gap: spacing.md }}>
          <View style={{ flex: 1 }}>
            <Input
              label="Warranty expires"
              value={warranty}
              onChangeText={setWarranty}
              placeholder="YYYY-MM-DD"
              autoCapitalize="none"
            />
          </View>
          <View style={{ flex: 1 }}>
            <Input
              label="Next service"
              value={service}
              onChangeText={setService}
              placeholder="YYYY-MM-DD"
              autoCapitalize="none"
            />
          </View>
        </View>
        <Text variant="labelMd" color={colors.textMuted}>
          Set either date and AssetShield reminds you before it arrives.
        </Text>
        <Button title="Save changes" loading={save.isPending} onPress={() => save.mutate()} />
        <Button
          title={(a.receiptCount ?? a.receipts?.length ?? 0) > 0 ? 'Add another receipt' : 'Add receipt'}
          variant="secondary"
          onPress={addReceipt}
        />
        <Button
          title="QR label"
          variant="secondary"
          onPress={() => router.push(`/(app)/asset/${assetId}/qr` as never)}
        />
        <Button title="Delete asset" variant="danger" onPress={remove} />
      </View>
    </Screen>
  );
}

/** A real calendar date in YYYY-MM-DD (rejects 2026-02-31 and the like). */
function isIsoDate(v: string): boolean {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(v)) return false;
  const d = new Date(`${v}T00:00:00Z`);
  return !Number.isNaN(d.getTime()) && d.toISOString().slice(0, 10) === v;
}
