import { Ionicons } from '@expo/vector-icons';
import { router } from 'expo-router';
import { Pressable, View } from 'react-native';
import { useAuth } from '@/lib/auth/AuthProvider';
import { useUnreadCount } from '@/lib/notifications/unread';
import { Text } from './Text';
import { colors } from '@/theme';

/**
 * Always-visible alerts entry point. Owners and agents have no notifications tab,
 * so this bell is how they reach and notice alerts — with an unread badge so
 * they know something is waiting without having to go looking. Admins already
 * have a dedicated Alerts tab, so the bell hides for them to avoid duplication.
 */
export function NotificationBell({ tint = colors.text }: { tint?: string }) {
  const { user } = useAuth();
  const unread = useUnreadCount();

  if (user?.role === 'ADMIN') return null;
  const label = unread > 9 ? '9+' : String(unread);

  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={unread > 0 ? `Alerts, ${unread} unread` : 'Alerts'}
      hitSlop={10}
      onPress={() => router.push('/(app)/(tabs)/notifications' as never)}
      style={{ padding: 4 }}
    >
      <Ionicons name="notifications-outline" size={24} color={tint} />
      {unread > 0 ? (
        <View
          style={{
            position: 'absolute',
            top: 0,
            right: 0,
            minWidth: 16,
            height: 16,
            borderRadius: 8,
            paddingHorizontal: 3,
            backgroundColor: colors.error,
            alignItems: 'center',
            justifyContent: 'center',
            borderWidth: 1.5,
            borderColor: colors.background,
          }}
        >
          <Text variant="labelMd" weight="semibold" color={colors.white} style={{ fontSize: 9, lineHeight: 12 }}>
            {label}
          </Text>
        </View>
      ) : null}
    </Pressable>
  );
}
