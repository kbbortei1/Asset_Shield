import { Ionicons } from '@expo/vector-icons';
import { CameraView, useCameraPermissions } from 'expo-camera';
import { router } from 'expo-router';
import { useRef, useState } from 'react';
import { View } from 'react-native';
import { Button, Card, Header, Screen, Text } from '@/components/ui';
import { colors, radius, spacing } from '@/theme';

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/** Extracts the asset id from a scanned AssetShield QR label (or a bare UUID). */
function assetIdFrom(data: string): string | null {
  const match = data.trim().match(/^assetshield:\/\/asset\/([0-9a-f-]{36})$/i);
  if (match && UUID_RE.test(match[1])) return match[1];
  if (UUID_RE.test(data.trim())) return data.trim();
  return null;
}

/** QR scanner for asset labels — opens the asset's evidence record. */
export default function ScanAssetQr() {
  const [permission, requestPermission] = useCameraPermissions();
  const [badScan, setBadScan] = useState(false);
  // a QR in frame fires the callback repeatedly — only the first one may navigate
  const handled = useRef(false);

  const onScanned = ({ data }: { data: string }) => {
    if (handled.current) return;
    const assetId = assetIdFrom(data);
    if (!assetId) {
      setBadScan(true);
      return;
    }
    handled.current = true;
    router.replace(`/(app)/asset/${assetId}` as never);
  };

  if (!permission) return null;

  if (!permission.granted) {
    return (
      <Screen>
        <Header title="Scan asset label" />
        <Card style={{ alignItems: 'center', gap: spacing.lg, paddingVertical: spacing.xl }}>
          <Ionicons name="qr-code-outline" size={48} color={colors.primary} />
          <Text variant="bodyMd" style={{ textAlign: 'center' }}>
            Allow camera access to scan the QR label on an asset and jump straight
            to its evidence record.
          </Text>
          <Button title="Allow camera" onPress={() => requestPermission()} />
        </Card>
      </Screen>
    );
  }

  return (
    <Screen scroll={false}>
      <Header title="Scan asset label" />
      <View style={{ flex: 1, borderRadius: radius.lg, overflow: 'hidden' }}>
        <CameraView
          style={{ flex: 1 }}
          barcodeScannerSettings={{ barcodeTypes: ['qr'] }}
          onBarcodeScanned={onScanned}
        />
      </View>
      <Text variant="labelMd" color={badScan ? colors.error : colors.textMuted} style={{ textAlign: 'center', marginTop: spacing.md }}>
        {badScan ? 'Not an AssetShield label. Try another code.' : 'Point the camera at an AssetShield QR label.'}
      </Text>
    </Screen>
  );
}
