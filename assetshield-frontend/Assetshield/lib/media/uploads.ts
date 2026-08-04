import NetInfo from '@react-native-community/netinfo';
import {
  AssetCategory,
  AssetPhotoInput,
  CreateAssetMetadata,
  DamagePhotoMetadata,
  DamagePhotoUploadResult,
  Asset,
  isApiError,
  isDuplicateHash,
  propertiesApi,
  damageApi,
} from '@/lib/api';
import { enqueueUpload } from '@/lib/offline/queue';
import { buildAssetMultiForm, buildDamageForm, CapturedImage, getCurrentCoords } from './capture';
import { sha256OfFile } from './hash';

export type UploadOutcome<T> =
  | { status: 'uploaded'; data: T }
  | { status: 'duplicate' }
  | { status: 'queued' };

/** offline with >1 photo — a multi-photo asset can't be queued as one job yet */
export type AssetUploadOutcome = UploadOutcome<Asset> | { status: 'needs-online' };

async function isOnline(): Promise<boolean> {
  const s = await NetInfo.fetch();
  return !!s.isConnected && s.isInternetReachable !== false;
}

/** One staged photo for a multi-photo asset: its image + confirmed fix + capture time. */
export type StagedPhoto = {
  image: CapturedImage;
  coords?: { gpsLat?: number; gpsLng?: number };
  capturedAt: string;
};

/**
 * Create ONE asset from 1..15 photos: hash each file's exact bytes (LAST),
 * build the multipart body (repeated `file` parts + shared metadata), and
 * upload — or, for a single photo, queue it if offline. A duplicate hash (409)
 * is surfaced as "already documented". A multi-photo asset can't be queued as
 * one job yet, so offline with >1 photo returns 'needs-online'.
 */
export async function uploadAssetMulti(
  propertyId: string,
  items: StagedPhoto[],
  shared: { description: string; estimatedValue?: number; category?: AssetCategory },
): Promise<AssetUploadOutcome> {
  const photos: AssetPhotoInput[] = [];
  for (const it of items) {
    const sha256Hash = await sha256OfFile(it.image.uri); // hash LAST, per photo
    photos.push({ sha256Hash, gpsLat: it.coords?.gpsLat, gpsLng: it.coords?.gpsLng, capturedAt: it.capturedAt });
  }
  const metadata: CreateAssetMetadata = {
    description: shared.description,
    estimatedValue: shared.estimatedValue,
    category: shared.category,
    photos,
  };
  const endpoint = `/properties/${propertyId}/assets`;

  if (!(await isOnline())) {
    if (items.length === 1) {
      await enqueueSingle(endpoint, items[0].image, metadata, shared.description);
      return { status: 'queued' };
    }
    return { status: 'needs-online' };
  }

  try {
    const data = await propertiesApi.uploadAsset(
      propertyId,
      buildAssetMultiForm(items.map((i) => i.image), metadata),
    );
    return { status: 'uploaded', data };
  } catch (e) {
    if (isDuplicateHash(e)) return { status: 'duplicate' };
    if (isApiError(e) && e.httpStatus === 0) {
      if (items.length === 1) {
        await enqueueSingle(endpoint, items[0].image, metadata, shared.description);
        return { status: 'queued' };
      }
      return { status: 'needs-online' };
    }
    throw e;
  }
}

/** A single-photo asset replays fine against the multi endpoint (file list of 1). */
async function enqueueSingle(endpoint: string, image: CapturedImage, metadata: CreateAssetMetadata, label: string) {
  await enqueueUpload({
    kind: 'asset',
    endpoint,
    fileUri: image.uri,
    fileName: image.name,
    mimeType: image.type,
    metadata,
    label,
  });
}

/** Capture a damage photo end-to-end (same recipe; nested response). */
export async function uploadDamagePhoto(
  reportId: string,
  image: CapturedImage,
  fields: {
    description?: string;
    useGps?: boolean;
    /** the fix the user CONFIRMED on screen — upload exactly what was shown */
    coords?: { gpsLat?: number; gpsLng?: number };
  },
): Promise<UploadOutcome<DamagePhotoUploadResult>> {
  const coords = fields.useGps === false ? {} : (fields.coords ?? (await getCurrentCoords()));
  const sha256Hash = await sha256OfFile(image.uri);
  const metadata: DamagePhotoMetadata = {
    sha256Hash,
    ...coords,
    capturedAt: new Date().toISOString(),
    description: fields.description,
  };

  const endpoint = `/damage-reports/${reportId}/photos`;
  if (!(await isOnline())) {
    await enqueueUpload({
      kind: 'damage',
      endpoint,
      fileUri: image.uri,
      fileName: image.name,
      mimeType: image.type,
      metadata,
      label: fields.description ?? 'Damage photo',
    });
    return { status: 'queued' };
  }

  try {
    const data = await damageApi.uploadPhoto(reportId, buildDamageForm(image, metadata));
    return { status: 'uploaded', data };
  } catch (e) {
    if (isDuplicateHash(e)) return { status: 'duplicate' };
    if (isApiError(e) && e.httpStatus === 0) {
      await enqueueUpload({
        kind: 'damage',
        endpoint,
        fileUri: image.uri,
        fileName: image.name,
        mimeType: image.type,
        metadata,
        label: fields.description ?? 'Damage photo',
      });
      return { status: 'queued' };
    }
    throw e;
  }
}
