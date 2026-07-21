-- Per-user channel switches. Default TRUE so existing users keep getting alerts.
-- in_app_enabled gates the Alerts-tab history row; push_enabled gates FCM banners.
ALTER TABLE notification_preferences
  ADD COLUMN push_enabled BOOLEAN NOT NULL DEFAULT TRUE,
  ADD COLUMN in_app_enabled BOOLEAN NOT NULL DEFAULT TRUE;
