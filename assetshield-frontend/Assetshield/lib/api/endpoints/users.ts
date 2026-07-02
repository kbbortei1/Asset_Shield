import { api } from '../client';
import { UserProfile } from '../models';

export const usersApi = {
  me: () => api.get<UserProfile>('/users/me'),
  updateMe: (body: { fullName?: string; language?: string }) => api.put<UserProfile>('/users/me', body),
  /** KYC upload — multipart `file` part only. */
  uploadGhanaCard: (form: FormData) => api.upload<UserProfile>('/users/me/ghana-card', form),
  /** GDPR-style erasure request. */
  requestErasure: () => api.del<void>('/users/me'),
  // admin
  createAdmin: (body: { phoneNumber: string; password: string; fullName: string }) =>
    api.post<UserProfile>('/admin/admins', body),
};
