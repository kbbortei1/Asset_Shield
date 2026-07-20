import { api } from '../client';
import {
  CreateDamageReportRequest,
  CreatePairRequest,
  DamagePhotoUploadResult,
  DamageReport,
  Dossier,
  DossierDownload,
  DossierStatusResult,
  GenerateDossierResult,
  Pair,
  PairingSuggestion,
} from '../models';
import { Page, PageParams } from '../types';

export const damageApi = {
  create: (propertyId: string, body: CreateDamageReportRequest) =>
    api.post<DamageReport>(`/properties/${propertyId}/damage-reports`, body),
  listByProperty: (propertyId: string, params?: PageParams) =>
    api.get<Page<DamageReport>>(`/properties/${propertyId}/damage-reports`, { query: params }),
  myReports: (params?: PageParams) => api.get<Page<DamageReport>>('/users/me/damage-reports', { query: params }),
  get: (reportId: string) => api.get<DamageReport>(`/damage-reports/${reportId}`),

  /** Upload a damage photo — multipart. Response is NESTED (photo + pairingSuggestions). */
  uploadPhoto: (reportId: string, form: FormData) =>
    api.upload<DamagePhotoUploadResult>(`/damage-reports/${reportId}/photos`, form),
  pairingSuggestions: (reportId: string, photoId: string, radiusM = 25) =>
    api.get<PairingSuggestion[]>(`/damage-reports/${reportId}/photos/${photoId}/pairing-suggestions`, {
      query: { radiusM },
    }),
  pair: (reportId: string, body: CreatePairRequest) => api.post<Pair>(`/damage-reports/${reportId}/pairs`, body),
  unpair: (reportId: string, pairId: string) => api.del<void>(`/damage-reports/${reportId}/pairs/${pairId}`),
  complete: (reportId: string) => api.put<DamageReport>(`/damage-reports/${reportId}/complete`),

  // dossiers
  generateDossier: (reportId: string) =>
    api.post<GenerateDossierResult>(`/damage-reports/${reportId}/generate-dossier`),
  /** Fresh checkout for an unpaid dossier — resume payment any time. */
  dossierPay: (dossierId: string) => api.post<GenerateDossierResult>(`/dossiers/${dossierId}/pay`),
  dossierStatus: (dossierId: string) => api.get<DossierStatusResult>(`/dossiers/${dossierId}/status`),
  dossierDownload: (dossierId: string) => api.get<DossierDownload>(`/dossiers/${dossierId}/download`),
  rotateShareToken: (dossierId: string) => api.post<{ shareToken: string }>(`/dossiers/${dossierId}/rotate-share-token`),
  retryGeneration: (dossierId: string) => api.post<DossierStatusResult>(`/dossiers/${dossierId}/retry-generation`),
  myDossiers: (params?: PageParams) => api.get<Page<Dossier>>('/users/me/dossiers', { query: params }),
};
