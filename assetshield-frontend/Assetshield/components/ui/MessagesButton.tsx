import { Ionicons } from '@expo/vector-icons';
import { router } from 'expo-router';
import { Pressable, View } from 'react-native';
import { useAuth } from '@/lib/auth/AuthProvider';
import { colors, spacing } from '@/theme';
import { NotificationBell } from './NotificationBell';

/**
 * Quick entry to the Messages list (owner<->insurer conversations). Sits next
 * to the notification bell in headers. Hidden for admins, who don't chat.
 */
export function MessagesButton({ tint = colors.text }: { tint?: string }) {
  const { user } = useAuth();
  if (user?.role === 'ADMIN') return null;
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel="Messages"
      hitSlop={10}
      onPress={() => router.push('/(app)/messages' as never)}
      style={{ padding: 4 }}
    >
      <Ionicons name="chatbubbles-outline" size={24} color={tint} />
    </Pressable>
  );
}

/** Header cluster: Messages + Alerts, so both are always one tap away. */
export function HeaderActions({ tint = colors.text }: { tint?: string }) {
  return (
    <View style={{ flexDirection: 'row', alignItems: 'center', gap: spacing.sm }}>
      <MessagesButton tint={tint} />
      <NotificationBell tint={tint} />
    </View>
  );
}
