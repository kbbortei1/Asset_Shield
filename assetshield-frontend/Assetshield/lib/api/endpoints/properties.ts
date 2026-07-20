import { api } from '../client';
import { API_BASE_URL } from '../config';
import { ApiError } from '../errors';
import {
  Asset,
  AssetSearchFilters,
  CreatePropertyRequest,
  Invitation,
  InviteRequest,
  Member,
  Property,
  TimelineEvent,
} from '../models';
import { getAccessToken } from '../tokens';
import { Page, PageParams } from '../types';

export const propertiesApi = {
  list: (params?: PageParams) => api.get<Page<Property>>('/properties', { query: params }),
  create: (body: CreatePropertyRequest) => api.post<Property>('/properties', body),
  get: (id: string) => api.get<Property>(`/properties/${id}`),
  update: (id: string, body: Partial<CreatePropertyRequest>) => api.put<Property>(`/properties/${id}`, body),
  remove: (id: string) => api.del<void>(`/properties/${id}`),
  setOffersOptin: (id: string, openToOffers: boolean) =>
    api.put<Property>(`/properties/${id}/offers-optin`, { openToOffers }),

  // assets
  listAssets: (propertyId: string, params?: PageParams & AssetSearchFilters) =>
    api.get<Page<Asset>>(`/properties/${propertyId}/assets`, { query: params }),
  /** Upload an asset photo — multipart `file` + `metadata` JSON parts (§4). Response is FLAT. */
  uploadAsset: (propertyId: string, form: FormData) =>
    api.upload<Asset>(`/properties/${propertyId}/assets`, form),
  getAsset: (assetId: string) => api.get<Asset>(`/assets/${assetId}`),
  updateAsset: (
    assetId: string,
    body: {
      description?: string;
      estimatedValue?: number;
      warrantyExpiresOn?: string;
      nextServiceOn?: string;
    },
  ) => api.put<Asset>(`/assets/${assetId}`, body),
  removeAsset: (assetId: string) => api.del<void>(`/assets/${assetId}`),
  uploadReceipt: (assetId: string, form: FormData) => api.upload<Asset>(`/assets/${assetId}/receipts`, form),

  // insights
  timeline: (propertyId: string) =>
    api.get<{ items: TimelineEvent[] }>(`/properties/${propertyId}/timeline`).then((r) => r.items ?? []),
  /**
   * CSV export — raw text/csv, not the JSON envelope, so it bypasses the
   * typed client. Caller writes the string to a file and shares it.
   */
  exportAssetsCsv: async (propertyId: string): Promise<string> => {
    const access = await getAccessToken();
    const res = await fetch(`${API_BASE_URL}/properties/${propertyId}/assets/export`, {
      headers: access ? { Authorization: `Bearer ${access}` } : {},
    });
    if (!res.ok) throw new ApiError({ code: res.status === 403 ? 'FORBIDDEN' : 'INTERNAL_ERROR', httpStatus: res.status });
    return res.text();
  },

  // household (these two endpoints return a raw { items: [...] }, not a Page)
  invite: (propertyId: string, body: InviteRequest) =>
    api.post<Invitation>(`/properties/${propertyId}/invite`, body),
  members: (propertyId: string) =>
    api.get<{ items: Member[] }>(`/properties/${propertyId}/members`).then((r) => r.items ?? []),
  removeMember: (propertyId: string, userId: string) =>
    api.del<void>(`/properties/${propertyId}/members/${userId}`),

  // invitations (invitee side)
  myInvitations: () => api.get<{ items: Invitation[] }>('/users/me/invitations').then((r) => r.items ?? []),
  respondInvitation: (invitationId: string, accept: boolean) =>
    api.put<Invitation>(`/invitations/${invitationId}/respond`, { accept }),
};
