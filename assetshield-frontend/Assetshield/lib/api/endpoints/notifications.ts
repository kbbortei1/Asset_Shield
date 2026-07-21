import { api } from '../client';
import { AppNotification, DeviceTokenRequest, NotificationPreferences, Tip } from '../models';
import { Page, PageParams } from '../types';

export const notificationsApi = {
  registerDeviceToken: (body: DeviceTokenRequest) => api.put<void>('/users/me/device-token', body),
  deleteDeviceToken: (fcmToken: string) => api.del<void>('/users/me/device-token', { fcmToken }),
  getPreferences: () => api.get<NotificationPreferences>('/users/me/notification-preferences'),
  /** Partial update — send only the fields you're changing. */
  updatePreferences: (body: Partial<NotificationPreferences>) =>
    api.put<NotificationPreferences>('/users/me/notification-preferences', body),
  list: (params?: PageParams) => api.get<Page<AppNotification>>('/users/me/notifications', { query: params }),

  // tips
  feed: (params?: PageParams) => api.get<Page<Tip>>('/tips/feed', { query: params }),
  propertyTips: (propertyId: string, params?: PageParams) =>
    api.get<Page<Tip>>(`/properties/${propertyId}/tips`, { query: params }),
  markTipRead: (tipId: string) => api.put<void>(`/tips/${tipId}/read`),
};
