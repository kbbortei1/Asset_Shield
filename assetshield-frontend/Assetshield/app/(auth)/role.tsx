import { Ionicons } from '@expo/vector-icons';
import { router } from 'expo-router';
import { View } from 'react-native';
import { Card, Header, Screen, Text } from '@/components/ui';
import { colors, spacing } from '@/theme';

/** Stitch: "Role Selection". Owner vs Agent — drives register vs register-agent. */
export default function RoleSelect() {
  return (
    <Screen scroll={false}>
      <Header title="" />
      <View style={{ gap: spacing.sm }}>
        <Text variant="headlineLgMobile">How will you use AssetShield?</Text>
        <Text variant="bodyMd" color={colors.textMuted}>
          You can always change this later from your profile.
        </Text>
      </View>

      <View style={{ gap: spacing.lg, marginTop: spacing.lg }}>
        <RoleCard
          icon="home"
          title="I'm a property owner"
          body="Document your home or business assets and build a tamper-evident record."
          onPress={() => router.push('/(auth)/permissions?role=owner' as never)}
        />
        <RoleCard
          icon="briefcase"
          title="I'm an insurance agent"
          body="Discover leads, receive shared dossiers, and send quotes to owners."
          onPress={() => router.push('/(auth)/permissions?role=agent' as never)}
        />
      </View>
    </Screen>
  );
}

function RoleCard({
  icon,
  title,
  body,
  onPress,
}: {
  icon: keyof typeof Ionicons.glyphMap;
  title: string;
  body: string;
  onPress: () => void;
}) {
  return (
    <Card onPress={onPress}>
      <View style={{ flexDirection: 'row', gap: spacing.lg, alignItems: 'center' }}>
        <View
          style={{
            width: 56,
            height: 56,
            borderRadius: 16,
            backgroundColor: colors.tealTint,
            alignItems: 'center',
            justifyContent: 'center',
          }}
        >
          <Ionicons name={icon} size={28} color={colors.primary} />
        </View>
        <View style={{ flex: 1, gap: 4 }}>
          <Text variant="headlineSm">{title}</Text>
          <Text variant="bodyMd" color={colors.textMuted}>
            {body}
          </Text>
        </View>
        <Ionicons name="chevron-forward" size={22} color={colors.textMuted} />
      </View>
    </Card>
  );
}
