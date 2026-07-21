import { api } from '../client';
import { AssetAnalytics, AuditEvent, UserProfile } from '../models';
import { Page, PageParams } from '../types';

export const usersApi = {
  me: () => api.get<UserProfile>('/users/me'),
  /** Portfolio rollup across every accessible property. */
  assetAnalytics: () => api.get<AssetAnalytics>('/users/me/asset-analytics'),
  /** Re-auth gate for destructive actions — true if the password matches. */
  verifyPassword: (password: string) =>
    api.post<{ verified: boolean }>('/users/me/verify-password', { password }).then((r) => r.verified),
  updateMe: (body: { fullName?: string; language?: string }) => api.put<UserProfile>('/users/me', body),
  /** KYC upload — multipart `file` part only. */
  uploadGhanaCard: (form: FormData) => api.upload<UserProfile>('/users/me/ghana-card', form),
  /** Profile picture — multipart `file` part; returns the updated profile. */
  uploadAvatar: (form: FormData) => api.upload<UserProfile>('/users/me/avatar', form),
  /** GDPR-style erasure request. */
  requestErasure: () => api.del<void>('/users/me'),
  // admin
  createAdmin: (body: { phoneNumber: string; password: string; fullName: string }) =>
    api.post<UserProfile>('/admin/admins', body),
  auditEvents: (params?: PageParams & { action?: string }) =>
    api.get<Page<AuditEvent>>('/admin/audit-events', { query: params }),
};
