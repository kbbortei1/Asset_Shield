import { Ionicons } from '@expo/vector-icons';
import { Image, ImageContentFit } from 'expo-image';
import { Pressable, View, ViewStyle } from 'react-native';
import { resolveMediaUrl } from '@/lib/api';
import { colors } from '@/theme';
import { useImageViewer } from './ImageViewer';

/**
 * Image for API-served media. Resolves relative signed URLs against the API
 * origin (local storage returns relative paths) and shows a placeholder when
 * there's no image. Always feed it the freshly-fetched URL (they expire ~15min).
 */
export function RemoteImage({
  uri,
  width,
  height,
  radius = 0,
  contentFit = 'cover',
  zoomable = false,
  style,
  cacheKey,
}: {
  uri?: string | null;
  width?: number | `${number}%`;
  height: number;
  radius?: number;
  contentFit?: ImageContentFit;
  /** tap to open the full-screen zoomable viewer */
  zoomable?: boolean;
  style?: ViewStyle;
  /**
   * Stable identity for the disk cache. Signed URLs rotate every ~15min, which
   * would otherwise bust the cache on every refetch — pass something stable
   * (the photo's sha256 hash is ideal) so each image downloads once, ever.
   */
  cacheKey?: string;
}) {
  const resolved = resolveMediaUrl(uri);
  const w = width ?? '100%';
  const viewer = useImageViewer();

  if (!resolved) {
    return (
      <View
        style={[
          { width: w, height, borderRadius: radius, overflow: 'hidden', backgroundColor: colors.border, alignItems: 'center', justifyContent: 'center' },
          style,
        ]}
      >
        <Ionicons name="image-outline" size={Math.min(28, height / 3)} color={colors.textMuted} />
      </View>
    );
  }
  const img = (
    <Image
      source={{ uri: resolved, cacheKey }}
      style={[{ width: w, height, borderRadius: radius }, style as object]}
      contentFit={contentFit}
      transition={150}
    />
  );
  if (zoomable) {
    return <Pressable onPress={() => viewer.open(resolved)}>{img}</Pressable>;
  }
  return img;
}
