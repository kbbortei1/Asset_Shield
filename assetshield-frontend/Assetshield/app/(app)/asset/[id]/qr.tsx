import { useQuery } from '@tanstack/react-query';
import { useLocalSearchParams } from 'expo-router';
import { View } from 'react-native';
import QRCode from 'react-native-qrcode-svg';
import { propertiesApi } from '@/lib/api';
import { Card, Header, Loading, Screen, Text, VerifiedBadge } from '@/components/ui';
import { colors, spacing } from '@/theme';

/**
 * Printable QR label for an asset. The code encodes the app deep link
 * (assetshield://asset/<id>); scanning it inside the app — or any camera app
 * with AssetShield installed — opens this asset's evidence record. Access is
 * still enforced server-side, so a stranger scanning the label sees nothing.
 */
export default function AssetQrLabel() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const assetId = id!;
  const q = useQuery({ queryKey: ['asset', assetId], queryFn: () => propertiesApi.getAsset(assetId) });

  if (q.isLoading) return <Loading />;
  const a = q.data;

  return (
    <Screen>
      <Header title="QR label" />
      <Card style={{ alignItems: 'center', gap: spacing.lg, paddingVertical: spacing.xl }}>
        <Text variant="headlineSm" style={{ textAlign: 'center' }}>
          {a?.description ?? 'Asset'}
        </Text>
        <View style={{ padding: spacing.lg, backgroundColor: colors.white, borderRadius: 12 }}>
          <QRCode value={`assetshield://asset/${assetId}`} size={220} />
        </View>
        {a?.sha256Hash ? <VerifiedBadge hash={a.sha256Hash} /> : null}
        <Text variant="labelMd" color={colors.textMuted} style={{ textAlign: 'center' }}>
          Print or screenshot this label and stick it on the asset. Scanning it in
          AssetShield opens this record instantly.
        </Text>
      </Card>
    </Screen>
  );
}
