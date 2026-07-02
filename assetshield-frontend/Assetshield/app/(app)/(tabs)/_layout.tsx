import { Ionicons } from '@expo/vector-icons';
import { Tabs } from 'expo-router';
import { View } from 'react-native';
import { QuickAction, QuickMenu } from '@/components/nav/QuickMenu';
import { useAuth } from '@/lib/auth/AuthProvider';
import { colors, fontFamily } from '@/theme';

type Role = 'OWNER' | 'MEMBER' | 'AGENT' | 'ADMIN';

export default function TabsLayout() {
  const { user } = useAuth();
  const role = (user?.role ?? 'OWNER') as Role;
  const isOwner = role === 'OWNER' || role === 'MEMBER';
  const isAgent = role === 'AGENT';
  const isAdmin = role === 'ADMIN';

  const icon = (name: keyof typeof Ionicons.glyphMap) => {
    const TabIcon = ({ color, size, focused }: { color: string; size: number; focused: boolean }) => (
      <View style={{ alignItems: 'center' }}>
        <View
          style={{
            height: 3,
            width: 18,
            borderRadius: 2,
            marginBottom: 5,
            backgroundColor: focused ? colors.primary : 'transparent',
          }}
        />
        <Ionicons name={name} color={color} size={size} />
      </View>
    );
    TabIcon.displayName = `TabIcon(${name})`;
    return TabIcon;
  };

  // Tabs visible in the bar (max 4). Overflow screens are kept as routes via
  // href:null and surfaced through the center "+" QuickMenu.
  const show = (visible: boolean) => (visible ? undefined : null);

  // market tab adapts per role (Leads for agents, Agents for admin)
  const marketTitle = isAgent ? 'Leads' : 'Agents';
  const marketIcon = isAgent ? 'pricetags' : 'shield-checkmark';

  const quickActions: QuickAction[] = isOwner
    ? [
        { icon: 'add-circle', label: 'New property', href: '/(app)/property/new' },
        { icon: 'people', label: 'Connections', href: '/(app)/(tabs)/market' },
        { icon: 'notifications', label: 'Alerts', href: '/(app)/(tabs)/notifications' },
        { icon: 'bulb', label: 'Safety tips', href: '/(app)/tips' },
      ]
    : isAgent
      ? [
          { icon: 'notifications', label: 'Alerts', href: '/(app)/(tabs)/notifications' },
          { icon: 'star', label: 'Subscription', href: '/(app)/subscription' },
        ]
      : [{ icon: 'person-add', label: 'Create an admin', href: '/(app)/admin/new' }];

  return (
    <View style={{ flex: 1 }}>
      <Tabs
        screenOptions={{
          headerShown: false,
          sceneStyle: { backgroundColor: colors.background },
          tabBarActiveTintColor: colors.primary,
          tabBarInactiveTintColor: colors.textMuted,
          tabBarStyle: {
            backgroundColor: colors.card,
            borderTopColor: colors.border,
            borderTopWidth: 0.5,
            height: 68,
            paddingBottom: 8,
            paddingTop: 4,
            shadowColor: '#11252B',
            shadowOpacity: 0.06,
            shadowRadius: 12,
            shadowOffset: { width: 0, height: -2 },
            elevation: 10,
          },
          tabBarLabelStyle: { fontFamily: fontFamily.interMedium, fontSize: 11 },
          tabBarItemStyle: { paddingTop: 2 },
        }}
      >
        {/* Home — all roles */}
        <Tabs.Screen name="home" options={{ title: 'Home', tabBarIcon: icon('home') }} />

        {/* slot 2: owner→Properties, agent→Leads, admin→Agents */}
        <Tabs.Screen
          name="properties"
          options={{ title: 'Properties', tabBarIcon: icon('business'), href: show(isOwner) }}
        />
        <Tabs.Screen
          name="market"
          options={{
            title: marketTitle,
            tabBarIcon: icon(marketIcon as keyof typeof Ionicons.glyphMap),
            href: show(isAgent || isAdmin),
          }}
        />

        {/* slot 3: owner/agent→Dossiers, admin→Alerts */}
        <Tabs.Screen
          name="activity"
          options={{ title: 'Dossiers', tabBarIcon: icon('document-text'), href: show(isOwner || isAgent) }}
        />
        <Tabs.Screen
          name="notifications"
          options={{ title: 'Alerts', tabBarIcon: icon('notifications'), href: show(isAdmin) }}
        />

        {/* slot 4: Profile — all roles */}
        <Tabs.Screen name="profile" options={{ title: 'Profile', tabBarIcon: icon('person') }} />
      </Tabs>

      <QuickMenu actions={quickActions} />
    </View>
  );
}
