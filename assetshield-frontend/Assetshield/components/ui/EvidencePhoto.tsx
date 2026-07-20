import { Ionicons } from '@expo/vector-icons';
import { Image } from 'expo-image';
import { View } from 'react-native';
import { colors, radius, spacing } from '@/theme';
import { RemoteImage } from './RemoteImage';
import { Text } from './Text';

export type EvidencePhotoProps = {
  /** Remote (API) photo URL — resolved via resolveMediaUrl. */
  uri?: string | null;
  /** Local file:// URI for pre-upload previews (bypasses URL resolution). */
  localUri?: string;
  height?: number;
  gpsLat?: number;
  gpsLng?: number;
  capturedAt?: string;
  /** Show the VERIFIED chip (pass the sha256 hash or simply true). */
  verified?: string | boolean;
  /** 'vault' = teal verified evidence; 'incident' = red damage evidence. */
  variant?: 'vault' | 'incident';
  zoomable?: boolean;
  radiusSize?: number;
};

function formatCoords(lat: number, lng: number): string {
  const la = `${Math.abs(lat).toFixed(4)}°${lat >= 0 ? 'N' : 'S'}`;
  const lo = `${Math.abs(lng).toFixed(4)}°${lng >= 0 ? 'E' : 'W'}`;
  return `${la} ${lo}`;
}

function formatStamp(iso: string): string {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '';
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`;
}

/**
 * A photo displayed AS EVIDENCE: verified chip + GPS/timestamp strip drawn
 * over the image (display-only — the stored file and its SHA-256 hash are
 * never modified; these are UI overlays fed by the metadata the backend
 * stores alongside the photo).
 */
export function EvidencePhoto({
  uri,
  localUri,
  height = 160,
  gpsLat,
  gpsLng,
  capturedAt,
  verified,
  variant = 'vault',
  zoomable,
  radiusSize,
}: EvidencePhotoProps) {
  const incident = variant === 'incident';
  const chipColor = incident ? colors.error : colors.success;
  const chipLabel = incident ? 'INCIDENT' : 'VERIFIED';
  const hasCoords = typeof gpsLat === 'number' && typeof gpsLng === 'number';
  const stamp = capturedAt ? formatStamp(capturedAt) : '';

  return (
    <View style={{ borderRadius: radiusSize ?? 0, overflow: 'hidden' }}>
      {localUri ? (
        <Image source={{ uri: localUri }} style={{ width: '100%', height }} contentFit="cover" />
      ) : (
        <RemoteImage
          uri={uri}
          height={height}
          zoomable={zoomable}
          cacheKey={typeof verified === 'string' && verified ? verified : undefined}
        />
      )}

      {verified ? (
        <View
          style={{
            position: 'absolute',
            top: spacing.sm,
            left: spacing.sm,
            flexDirection: 'row',
            alignItems: 'center',
            gap: 3,
            backgroundColor: chipColor,
            borderRadius: radius.sm,
            paddingHorizontal: 6,
            paddingVertical: 2,
          }}
        >
          <Ionicons name={incident ? 'alert-circle' : 'shield-checkmark'} size={10} color={colors.white} />
          <Text variant="labelMd" color={colors.white} style={{ fontSize: 9, letterSpacing: 0.8 }}>
            {chipLabel}
          </Text>
        </View>
      ) : null}

      {hasCoords || stamp ? (
        <View
          style={{
            position: 'absolute',
            left: 0,
            right: 0,
            bottom: 0,
            flexDirection: 'row',
            justifyContent: 'space-between',
            alignItems: 'center',
            backgroundColor: 'rgba(10, 16, 14, 0.62)',
            paddingHorizontal: spacing.sm,
            paddingVertical: 3,
            gap: spacing.sm,
          }}
        >
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: 3, flexShrink: 1 }}>
            {hasCoords ? (
              <>
                <Ionicons name="location" size={9} color={incident ? colors.warning : colors.cta} />
                <Text variant="labelMd" color={colors.white} numberOfLines={1} style={{ fontSize: 9, letterSpacing: 0.4 }}>
                  {formatCoords(gpsLat!, gpsLng!)}
                </Text>
              </>
            ) : null}
          </View>
          {stamp ? (
            <Text variant="labelMd" color={colors.white} style={{ fontSize: 9, letterSpacing: 0.4 }}>
              {stamp}
            </Text>
          ) : null}
        </View>
      ) : null}
    </View>
  );
}
