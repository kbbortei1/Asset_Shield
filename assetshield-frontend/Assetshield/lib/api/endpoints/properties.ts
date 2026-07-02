import { api } from '../client';
import {
  Asset,
  CreatePropertyRequest,
  Invitation,
  InviteRequest,
  Member,
  Property,
} from '../models';
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
  listAssets: (propertyId: string, params?: PageParams) =>
    api.get<Page<Asset>>(`/properties/${propertyId}/assets`, { query: params }),
  /** Upload an asset photo — multipart `file` + `metadata` JSON parts (§4). Response is FLAT. */
  uploadAsset: (propertyId: string, form: FormData) =>
    api.upload<Asset>(`/properties/${propertyId}/assets`, form),
  getAsset: (assetId: string) => api.get<Asset>(`/assets/${assetId}`),
  updateAsset: (assetId: string, body: { description?: string; estimatedValue?: number }) =>
    api.put<Asset>(`/assets/${assetId}`, body),
  removeAsset: (assetId: string) => api.del<void>(`/assets/${assetId}`),
  uploadReceipt: (assetId: string, form: FormData) => api.upload<Asset>(`/assets/${assetId}/receipts`, form),

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
