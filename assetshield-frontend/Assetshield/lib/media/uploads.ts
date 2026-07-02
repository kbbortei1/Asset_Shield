import NetInfo from '@react-native-community/netinfo';
import {
  AssetCategory,
  AssetMetadata,
  DamagePhotoMetadata,
  DamagePhotoUploadResult,
  Asset,
  isApiError,
  isDuplicateHash,
  propertiesApi,
  damageApi,
} from '@/lib/api';
import { enqueueUpload } from '@/lib/offline/queue';
import { buildAssetForm, buildDamageForm, CapturedImage, getCurrentCoords } from './capture';
import { sha256OfFile } from './hash';

export type UploadOutcome<T> =
  | { status: 'uploaded'; data: T }
  | { status: 'duplicate' }
  | { status: 'queued' };

async function isOnline(): Promise<boolean> {
  const s = await NetInfo.fetch();
  return !!s.isConnected && s.isInternetReachable !== false;
}

/**
 * Capture an asset photo end-to-end: hash the exact bytes (LAST), build the
 * multipart body, and upload immediately — or queue it if offline. A duplicate
 * hash (409) is surfaced as a friendly "already documented" outcome.
 */
export async function uploadAssetPhoto(
  propertyId: string,
  image: CapturedImage,
  fields: { description: string; estimatedValue?: number; category?: AssetCategory; useGps?: boolean },
): Promise<UploadOutcome<Asset>> {
  const coords = fields.useGps === false ? {} : await getCurrentCoords();
  const sha256Hash = await sha256OfFile(image.uri); // hash LAST
  const metadata: AssetMetadata = {
    sha256Hash,
    ...coords,
    capturedAt: new Date().toISOString(),
    description: fields.description,
    estimatedValue: fields.estimatedValue,
    category: fields.category,
  };

  const endpoint = `/properties/${propertyId}/assets`;
  if (!(await isOnline())) {
    await enqueueUpload({
      kind: 'asset',
      endpoint,
      fileUri: image.uri,
      fileName: image.name,
      mimeType: image.type,
      metadata,
      label: fields.description,
    });
    return { status: 'queued' };
  }

  try {
    const data = await propertiesApi.uploadAsset(propertyId, buildAssetForm(image, metadata));
    return { status: 'uploaded', data };
  } catch (e) {
    if (isDuplicateHash(e)) return { status: 'duplicate' };
    if (isApiError(e) && e.httpStatus === 0) {
      await enqueueUpload({
        kind: 'asset',
        endpoint,
        fileUri: image.uri,
        fileName: image.name,
        mimeType: image.type,
        metadata,
        label: fields.description,
      });
      return { status: 'queued' };
    }
    throw e;
  }
}

/** Capture a damage photo end-to-end (same recipe; nested response). */
export async function uploadDamagePhoto(
  reportId: string,
  image: CapturedImage,
  fields: { description?: string; useGps?: boolean },
): Promise<UploadOutcome<DamagePhotoUploadResult>> {
  const coords = fields.useGps === false ? {} : await getCurrentCoords();
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
