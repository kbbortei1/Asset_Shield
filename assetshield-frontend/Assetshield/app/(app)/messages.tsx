import { Ionicons } from '@expo/vector-icons';
import { useQuery } from '@tanstack/react-query';
import { router } from 'expo-router';
import { FlatList, Pressable, View } from 'react-native';
import { AgentInterest, marketplaceApi } from '@/lib/api';
import { useAuth } from '@/lib/auth/AuthProvider';
import { Card, EmptyState, ErrorState, Header, ListSkeleton, Screen, Text } from '@/components/ui';
import { colors, spacing } from '@/theme';

/**
 * All the conversations you can have: every ACCEPTED owner<->insurer connection.
 * Owners see the agents they're connected with; agents see all the owners who
 * shared a dossier / accepted them. Tap a row to open that chat.
 */
export default function Messages() {
  const { user } = useAuth();
  const isAgent = user?.role === 'AGENT';

  const q = useQuery({
    queryKey: ['conversations', isAgent ? 'agent' : 'owner'],
    queryFn: () => (isAgent ? marketplaceApi.agentInterests({ size: 100 }) : marketplaceApi.myAgentInterests({ size: 100 })),
    refetchInterval: 20_000,
  });

  const conversations = (q.data?.items ?? []).filter((it) => it.status === 'ACCEPTED');

  const otherName = (it: AgentInterest) => (isAgent ? it.ownerFullName : it.agentName) ?? (isAgent ? 'Owner' : 'Agent');
  const subtitle = (it: AgentInterest) =>
    isAgent ? it.propertyName ?? 'Property owner' : it.insurerName ?? 'Insurance agent';

  const open = (it: AgentInterest) =>
    router.push(`/(app)/chat/${it.interestId}?name=${encodeURIComponent(otherName(it))}` as never);

  return (
    <Screen scroll={false}>
      <Header title="Messages" />
      {q.isLoading ? (
        <ListSkeleton />
      ) : q.isError ? (
        <ErrorState onRetry={() => q.refetch()} />
      ) : conversations.length === 0 ? (
        <EmptyState
          icon="chatbubbles-outline"
          title="No conversations yet"
          body={isAgent
            ? 'When an owner shares a dossier or accepts your interest, your chat with them shows up here.'
            : 'Once you accept an agent’s interest, you can chat with them here.'}
        />
      ) : (
        <FlatList
          data={conversations}
          keyExtractor={(it) => it.interestId}
          contentContainerStyle={{ gap: spacing.sm, paddingBottom: spacing.xl }}
          renderItem={({ item }) => (
            <Pressable onPress={() => open(item)} accessibilityRole="button">
              <Card padded>
                <View style={{ flexDirection: 'row', alignItems: 'center', gap: spacing.md }}>
                  <View style={{ width: 44, height: 44, borderRadius: 22, backgroundColor: colors.tealTint, alignItems: 'center', justifyContent: 'center' }}>
                    <Ionicons name="person" size={22} color={colors.primary} />
                  </View>
                  <View style={{ flex: 1 }}>
                    <Text variant="bodyMd" weight="semibold" numberOfLines={1}>{otherName(item)}</Text>
                    <Text variant="labelMd" color={colors.textMuted} numberOfLines={1}>{subtitle(item)}</Text>
                  </View>
                  <Ionicons name="chevron-forward" size={18} color={colors.textMuted} />
                </View>
              </Card>
            </Pressable>
          )}
        />
      )}
    </Screen>
  );
}
