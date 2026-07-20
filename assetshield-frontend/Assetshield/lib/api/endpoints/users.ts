import { api } from '../client';
import { AssetAnalytics, AuditEvent, UserProfile } from '../models';
import { Page, PageParams } from '../types';

export const usersApi = {
  me: () => api.get<UserProfile>('/users/me'),
  /** Portfolio rollup across every accessible property. */
  assetAnalytics: () => api.get<AssetAnalytics>('/users/me/asset-analytics'),
  updateMe: (body: { fullName?: string; language?: string }) => api.put<UserProfile>('/users/me', body),
  /** KYC upload — multipart `file` part only. */
  uploadGhanaCard: (form: FormData) => api.upload<UserProfile>('/users/me/ghana-card', form),
  /** GDPR-style erasure request. */
  requestErasure: () => api.del<void>('/users/me'),
  // admin
  createAdmin: (body: { phoneNumber: string; password: string; fullName: string }) =>
    api.post<UserProfile>('/admin/admins', body),
  auditEvents: (params?: PageParams & { action?: string }) =>
    api.get<Page<AuditEvent>>('/admin/audit-events', { query: params }),
};
