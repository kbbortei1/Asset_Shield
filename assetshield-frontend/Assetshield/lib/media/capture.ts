import * as ImagePicker from 'expo-image-picker';
import * as Location from 'expo-location';
import { CreateAssetMetadata, DamagePhotoMetadata } from '@/lib/api';

export type CapturedImage = { uri: string; name: string; type: 'image/jpeg' | 'image/png' };

/**
 * Capture (camera) or pick (library) a single image. We deliberately do NOT
 * resize/re-encode after this point — the bytes we hash are the bytes we upload.
 * If compression is ever needed, do it HERE (before hashing), never after.
 */
export async function pickImage(source: 'camera' | 'library'): Promise<CapturedImage | null> {
  if (source === 'camera') {
    const perm = await ImagePicker.requestCameraPermissionsAsync();
    if (!perm.granted) throw new PermissionError('camera');
    const res = await ImagePicker.launchCameraAsync({
      mediaTypes: ['images'],
      quality: 0.85,
      exif: false,
    });
    return toCaptured(res);
  }
  const perm = await ImagePicker.requestMediaLibraryPermissionsAsync();
  if (!perm.granted) throw new PermissionError('library');
  const res = await ImagePicker.launchImageLibraryAsync({
    mediaTypes: ['images'],
    quality: 0.85,
    exif: false,
  });
  return toCaptured(res);
}

function toCaptured(res: ImagePicker.ImagePickerResult): CapturedImage | null {
  if (res.canceled || !res.assets?.length) return null;
  const a = res.assets[0];
  const isPng = (a.mimeType ?? '').includes('png') || a.uri.toLowerCase().endsWith('.png');
  const type = isPng ? 'image/png' : 'image/jpeg';
  const name = a.fileName ?? `photo.${isPng ? 'png' : 'jpg'}`;
  return { uri: a.uri, name, type };
}

/** Best-effort GPS — returns undefined coords if permission denied or unavailable. */
export async function getCurrentCoords(): Promise<{ gpsLat?: number; gpsLng?: number }> {
  try {
    const perm = await Location.requestForegroundPermissionsAsync();
    if (!perm.granted) return {};
    const pos = await Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.Balanced });
    return { gpsLat: pos.coords.latitude, gpsLng: pos.coords.longitude };
  } catch {
    return {};
  }
}

export type LocationFix = {
  gpsLat?: number;
  gpsLng?: number;
  /** metres, when the platform reports it */
  accuracy?: number;
  /** human-readable place, e.g. "Adabraka, Accra" (reverse-geocoded, offline-safe) */
  label?: string;
};

/**
 * A confirmable location fix: coordinates plus a reverse-geocoded label so the
 * user can SEE where the evidence will be geo-tagged before uploading. Uses
 * expo-location's built-in reverse geocoding (no API key; works in Expo Go).
 * Every field is best-effort — an empty fix means "no geo-tag".
 */
export async function getLocationFix(): Promise<LocationFix> {
  try {
    const perm = await Location.requestForegroundPermissionsAsync();
    if (!perm.granted) return {};
    const pos = await Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.Balanced });
    const fix: LocationFix = {
      gpsLat: pos.coords.latitude,
      gpsLng: pos.coords.longitude,
      accuracy: pos.coords.accuracy ?? undefined,
    };
    try {
      const places = await Location.reverseGeocodeAsync({
        latitude: pos.coords.latitude,
        longitude: pos.coords.longitude,
      });
      const p = places[0];
      if (p) {
        const parts = [p.district ?? p.subregion ?? p.street, p.city ?? p.region].filter(Boolean);
        fix.label = parts.length ? parts.join(', ') : undefined;
      }
    } catch {
      // label is a nicety; coords alone are still a valid fix
    }
    return fix;
  } catch {
    return {};
  }
}

/**
 * Build the multipart body for a multi-photo asset: one repeated `file` part
 * per image (SAME order as metadata.photos) + one `metadata` JSON part.
 */
export function buildAssetMultiForm(images: CapturedImage[], metadata: CreateAssetMetadata): FormData {
  const form = new FormData();
  for (const image of images) {
    form.append('file', { uri: image.uri, name: image.name, type: image.type } as any);
  }
  form.append('metadata', JSON.stringify(metadata));
  return form;
}

/** Build the multipart body for a damage-photo upload. */
export function buildDamageForm(image: CapturedImage, metadata: DamagePhotoMetadata): FormData {
  const form = new FormData();
  form.append('file', { uri: image.uri, name: image.name, type: image.type } as any);
  form.append('metadata', JSON.stringify(metadata));
  return form;
}

/** Build a single-file multipart body (KYC card / receipts). */
export function buildFileForm(image: CapturedImage): FormData {
  const form = new FormData();
  form.append('file', { uri: image.uri, name: image.name, type: image.type } as any);
  return form;
}

export class PermissionError extends Error {
  kind: 'camera' | 'library';
  constructor(kind: 'camera' | 'library') {
    super(`${kind} permission denied`);
    this.name = 'PermissionError';
    this.kind = kind;
  }
}
