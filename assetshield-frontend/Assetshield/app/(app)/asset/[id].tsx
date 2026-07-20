import { Ionicons } from '@expo/vector-icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { router, useLocalSearchParams } from 'expo-router';
import { useEffect, useState } from 'react';
import { Alert, View } from 'react-native';
import { propertiesApi } from '@/lib/api';
import { buildFileForm, pickImage } from '@/lib/media/capture';
import { Button, Card, EvidencePhoto, Header, Input, Loading, Screen, Text, VerifiedBadge, useToast } from '@/components/ui';
import { colors, spacing } from '@/theme';

export default function AssetDetail() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const assetId = id!;
  const qc = useQueryClient();
  const { show } = useToast();
  const q = useQuery({ queryKey: ['asset', assetId], queryFn: () => propertiesApi.getAsset(assetId) });

  const [description, setDescription] = useState('');
  const [value, setValue] = useState('');

  useEffect(() => {
    if (q.data) {
      setDescription(q.data.description);
      setValue(q.data.estimatedValue != null ? String(q.data.estimatedValue) : '');
    }
  }, [q.data]);

  const save = useMutation({
    mutationFn: () => propertiesApi.updateAsset(assetId, { description, estimatedValue: value ? Number(value) : undefined }),
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

  const remove = () =>
    Alert.alert('Delete asset?', 'This removes the asset from your records.', [
      { text: 'Cancel', style: 'cancel' },
      {
        text: 'Delete',
        style: 'destructive',
        onPress: async () => {
          const propertyId = q.data?.propertyId;
          try {
            await propertiesApi.removeAsset(assetId);
            if (propertyId) qc.invalidateQueries({ queryKey: ['assets', propertyId] });
            router.back();
          } catch (e: any) {
            Alert.alert('Could not delete', e?.message ?? 'Try again.');
          }
        },
      },
    ]);

  if (q.isLoading) return <Loading />;
  const a = q.data!;

  return (
    <Screen>
      <Header title="Asset" />
      <Card padded={false} style={{ overflow: 'hidden' }}>
        <EvidencePhoto
          uri={a.photoUrl}
          height={260}
          gpsLat={a.gpsLat}
          gpsLng={a.gpsLng}
          capturedAt={a.capturedAt}
          verified={a.sha256Hash}
          zoomable
        />
      </Card>

      <View style={{ flexDirection: 'row', gap: spacing.sm, flexWrap: 'wrap' }}>
        <VerifiedBadge hash={a.sha256Hash} />
        {a.capturedAt ? (
          <Text variant="labelMd" color={colors.textMuted}>
            <Ionicons name="time-outline" size={12} /> {new Date(a.capturedAt).toLocaleDateString()}
          </Text>
        ) : null}
        {a.gpsLat && a.gpsLng ? (
          <Text variant="labelMd" color={colors.textMuted}>
            <Ionicons name="location-outline" size={12} /> {a.gpsLat.toFixed(4)}, {a.gpsLng.toFixed(4)}
          </Text>
        ) : null}
      </View>

      <View style={{ gap: spacing.lg }}>
        <Input label="Description" value={description} onChangeText={setDescription} />
        <Input label="Estimated value (₵)" value={value} onChangeText={setValue} keyboardType="numeric" />
        <Button title="Save changes" loading={save.isPending} onPress={() => save.mutate()} />
        <Button
          title={(a.receiptCount ?? a.receipts?.length ?? 0) > 0 ? 'Add another receipt' : 'Add receipt'}
          variant="secondary"
          onPress={addReceipt}
        />
        <Button title="Delete asset" variant="danger" onPress={remove} />
      </View>
    </Screen>
  );
}
