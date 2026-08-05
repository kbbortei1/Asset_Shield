import { Ionicons } from '@expo/vector-icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useLocalSearchParams } from 'expo-router';
import { useMemo, useState } from 'react';
import { ActivityIndicator, FlatList, Pressable, TextInput, View } from 'react-native';
import { useKeyboardState } from 'react-native-keyboard-controller';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { InterestMessage, marketplaceApi } from '@/lib/api';
import { useAuth } from '@/lib/auth/AuthProvider';
import { EmptyState, ErrorState, Header, Loading, Screen, Text } from '@/components/ui';
import { colors, radius, spacing, type } from '@/theme';

/** Owner<->agent conversation, scoped to an accepted connection. Polls for replies. */
export default function Chat() {
  const { interestId, name } = useLocalSearchParams<{ interestId: string; name?: string }>();
  const { user } = useAuth();
  const myId = user?.id;
  const qc = useQueryClient();
  const [draft, setDraft] = useState('');
  const insets = useSafeAreaInsets();
  // Lift the whole column (messages + composer) by the keyboard height so the
  // composer lands on the keyboard and the list shrinks above it — explicit
  // math, so it works regardless of the device's soft-input mode.
  const kbHeight = useKeyboardState((s) => s.height);
  const lift = Math.max(0, kbHeight - insets.bottom);

  const q = useQuery({
    queryKey: ['messages', interestId],
    queryFn: () => marketplaceApi.messages(interestId!, { size: 100 }),
    refetchInterval: 4000, // lightweight polling — new replies appear within a few seconds
    refetchOnWindowFocus: true,
  });

  // API returns oldest-first; an inverted list wants newest-first.
  const items = useMemo(() => [...(q.data?.items ?? [])].reverse(), [q.data]);

  const send = useMutation({
    mutationFn: (body: string) => marketplaceApi.sendMessage(interestId!, body),
    onSuccess: () => {
      setDraft('');
      qc.invalidateQueries({ queryKey: ['messages', interestId] });
    },
  });

  const onSend = () => {
    const body = draft.trim();
    if (!body || send.isPending) return;
    send.mutate(body);
  };

  const composer = (
    <View style={{ flexDirection: 'row', alignItems: 'flex-end', gap: spacing.sm }}>
      <TextInput
        value={draft}
        onChangeText={setDraft}
        placeholder="Write a message…"
        placeholderTextColor={colors.textMuted}
        multiline
        maxLength={2000}
        style={{
          flex: 1,
          minHeight: 48,
          maxHeight: 120,
          borderRadius: radius.lg,
          borderWidth: 1,
          borderColor: colors.border,
          backgroundColor: colors.card,
          paddingHorizontal: spacing.lg,
          paddingTop: 12,
          paddingBottom: 12,
          color: colors.text,
          ...type.bodyMd,
        }}
      />
      <Pressable
        onPress={onSend}
        disabled={!draft.trim() || send.isPending}
        accessibilityRole="button"
        accessibilityLabel="Send message"
        style={{
          width: 48,
          height: 48,
          borderRadius: 24,
          alignItems: 'center',
          justifyContent: 'center',
          backgroundColor: draft.trim() ? colors.primary : colors.border,
        }}
      >
        {send.isPending ? (
          <ActivityIndicator color={colors.white} />
        ) : (
          <Ionicons name="send" size={18} color={colors.white} />
        )}
      </Pressable>
    </View>
  );

  if (q.isLoading) return <Loading />;
  if (q.isError) return <ErrorState onRetry={() => q.refetch()} />;

  return (
    // paddingBottom = keyboard lift: the whole column shifts up so the composer
    // sits on the keyboard and the message list shrinks above it (WhatsApp-style
    // — recent messages stay visible and history scrolls while typing).
    <Screen scroll={false} padded={false} contentStyle={{ paddingVertical: 0, gap: 0, paddingBottom: lift }}>
      <View style={{ paddingHorizontal: spacing.screenPadding }}>
        <Header title={name || 'Conversation'} />
      </View>
      <View style={{ flex: 1 }}>
        {items.length === 0 ? (
          <EmptyState
            icon="chatbubbles-outline"
            title="No messages yet"
            body="Start the conversation. Ask a question or share details about the property."
          />
        ) : (
          <FlatList
            data={items}
            keyExtractor={(m) => m.id}
            inverted
            style={{ flex: 1 }}
            keyboardShouldPersistTaps="handled"
            keyboardDismissMode="interactive"
            showsVerticalScrollIndicator={false}
            contentContainerStyle={{ paddingHorizontal: spacing.screenPadding, gap: spacing.sm, paddingVertical: spacing.sm }}
            renderItem={({ item }) => <Bubble message={item} mine={item.senderUserId === myId} />}
          />
        )}
      </View>
      <View style={{ paddingHorizontal: spacing.screenPadding, paddingTop: spacing.sm, backgroundColor: colors.background, borderTopWidth: 1, borderTopColor: colors.border }}>
        {composer}
      </View>
    </Screen>
  );
}

function Bubble({ message, mine }: { message: InterestMessage; mine: boolean }) {
  return (
    <View style={{ alignItems: mine ? 'flex-end' : 'flex-start' }}>
      <View
        style={{
          maxWidth: '82%',
          paddingHorizontal: spacing.md,
          paddingVertical: spacing.sm,
          borderRadius: radius.lg,
          borderBottomRightRadius: mine ? 4 : radius.lg,
          borderBottomLeftRadius: mine ? radius.lg : 4,
          backgroundColor: mine ? colors.primary : colors.card,
          borderWidth: mine ? 0 : 1,
          borderColor: colors.border,
        }}
      >
        <Text variant="bodyMd" color={mine ? colors.onPrimary : colors.text}>
          {message.body}
        </Text>
      </View>
      <Text variant="labelMd" color={colors.textMuted} style={{ fontSize: 10, marginTop: 2, marginHorizontal: 4 }}>
        {new Date(message.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
      </Text>
    </View>
  );
}
