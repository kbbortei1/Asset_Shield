import { Ionicons } from '@expo/vector-icons';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { router } from 'expo-router';
import { useEffect, useMemo, useState } from 'react';
import { Pressable, View } from 'react-native';
import { AdminUserItem, BroadcastAudience, isApiError, usersApi } from '@/lib/api';
import { Button, Card, Header, Input, Loading, Screen, Text, useToast, showAlert } from '@/components/ui';
import { colors, radius, spacing } from '@/theme';

type AudienceOption = { value: BroadcastAudience; label: string; sub: string; icon: keyof typeof Ionicons.glyphMap };
const AUDIENCES: AudienceOption[] = [
  { value: 'EVERYONE', label: 'Everyone', sub: 'All owners & agents', icon: 'people' },
  { value: 'OWNERS', label: 'Owners', sub: 'Every property owner', icon: 'home' },
  { value: 'AGENTS', label: 'Agents', sub: 'Every insurance agent', icon: 'briefcase' },
  { value: 'SPECIFIC', label: 'Specific people', sub: 'Pick individuals', icon: 'person-add' },
];

function useDebounced<T>(value: T, ms: number): T {
  const [v, setV] = useState(value);
  useEffect(() => {
    const t = setTimeout(() => setV(value), ms);
    return () => clearTimeout(t);
  }, [value, ms]);
  return v;
}

/** Admin: broadcast an in-app notification to a segment or hand-picked people. */
export default function Broadcast() {
  const { show } = useToast();
  const [audience, setAudience] = useState<BroadcastAudience>('EVERYONE');
  const [title, setTitle] = useState('');
  const [body, setBody] = useState('');
  const [selected, setSelected] = useState<Record<string, AdminUserItem>>({});
  const [search, setSearch] = useState('');
  const [busy, setBusy] = useState(false);
  const [inApp, setInApp] = useState(true);
  const [push, setPush] = useState(true);
  const q = useDebounced(search.trim(), 300);

  const counts = useQuery({ queryKey: ['audience-counts'], queryFn: () => usersApi.audienceCounts() });
  const directory = useQuery({
    queryKey: ['admin-users', q],
    queryFn: () => usersApi.adminUsers({ q: q || undefined, size: 50 }),
    enabled: audience === 'SPECIFIC',
    placeholderData: keepPreviousData,
  });

  const selectedIds = useMemo(() => Object.keys(selected), [selected]);
  const reach =
    audience === 'SPECIFIC'
      ? selectedIds.length
      : audience === 'OWNERS'
        ? counts.data?.owners ?? 0
        : audience === 'AGENTS'
          ? counts.data?.agents ?? 0
          : counts.data?.everyone ?? 0;

  const ready = title.trim().length > 0 && body.trim().length > 0 && reach > 0 && (inApp || push);

  const toggle = (u: AdminUserItem) =>
    setSelected((prev) => {
      const next = { ...prev };
      if (next[u.id]) delete next[u.id];
      else next[u.id] = u;
      return next;
    });

  const send = async () => {
    if (!ready) return;
    setBusy(true);
    try {
      const res = await usersApi.broadcast({
        audience,
        userIds: audience === 'SPECIFIC' ? selectedIds : undefined,
        title: title.trim(),
        body: body.trim(),
        inApp,
        push,
      });
      show(`Sent to ${res.recipientCount} ${res.recipientCount === 1 ? 'person' : 'people'}`);
      router.back();
    } catch (e) {
      showAlert('Could not send', isApiError(e) ? e.message : 'Please try again.');
    } finally {
      setBusy(false);
    }
  };

  if (counts.isLoading) return <Loading />;

  const countFor = (a: BroadcastAudience): number =>
    a === 'OWNERS'
      ? counts.data?.owners ?? 0
      : a === 'AGENTS'
        ? counts.data?.agents ?? 0
        : a === 'EVERYONE'
          ? counts.data?.everyone ?? 0
          : selectedIds.length;

  return (
    <Screen
      footer={
        <Button
          title={ready ? `Send to ${reach} ${reach === 1 ? 'person' : 'people'}` : 'Send broadcast'}
          loading={busy}
          disabled={!ready}
          onPress={send}
        />
      }
    >
      <Header title="Broadcast" />
      <Text variant="bodyMd" color={colors.textMuted}>
        Send a message to your users, a role, or specific people.
      </Text>

      {/* audience */}
      <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: spacing.md }}>
        {AUDIENCES.map((a) => {
          const active = audience === a.value;
          return (
            <Pressable key={a.value} onPress={() => setAudience(a.value)} style={{ width: '47%' }}>
              <View
                style={{
                  padding: spacing.md,
                  borderRadius: radius.lg,
                  borderWidth: active ? 2 : 1,
                  borderColor: active ? colors.primary : colors.border,
                  backgroundColor: active ? colors.tealTint : colors.card,
                  gap: 4,
                  minHeight: 92,
                }}
              >
                <Ionicons name={a.icon} size={20} color={active ? colors.primary : colors.textMuted} />
                <Text variant="bodyMd" weight="semibold">
                  {a.label}
                </Text>
                <Text variant="labelMd" color={colors.textMuted}>
                  {a.sub}
                </Text>
                <Text variant="labelMd" weight="semibold" color={active ? colors.primary : colors.textMuted}>
                  {a.value === 'SPECIFIC' ? `${selectedIds.length} selected` : `${countFor(a.value)} people`}
                </Text>
              </View>
            </Pressable>
          );
        })}
      </View>

      {/* specific-people picker */}
      {audience === 'SPECIFIC' ? (
        <View style={{ gap: spacing.sm }}>
          <Input
            placeholder="Search users by name or phone…"
            value={search}
            onChangeText={setSearch}
            autoCapitalize="none"
          />
          {directory.isLoading ? (
            <Loading />
          ) : (
            (directory.data?.items ?? []).map((u) => {
              const checked = !!selected[u.id];
              return (
                <Pressable key={u.id} onPress={() => toggle(u)}>
                  <Card padded style={{ borderWidth: checked ? 2 : 1, borderColor: checked ? colors.primary : colors.border }}>
                    <View style={{ flexDirection: 'row', alignItems: 'center', gap: spacing.md }}>
                      <Ionicons name={checked ? 'checkmark-circle' : 'ellipse-outline'} size={22} color={checked ? colors.primary : colors.textMuted} />
                      <View style={{ flex: 1 }}>
                        <Text variant="bodyMd" weight="semibold">
                          {u.fullName}
                        </Text>
                        <Text variant="labelMd" color={colors.textMuted}>
                          {u.phoneNumber} · {u.role === 'AGENT' ? 'Agent' : 'Owner'}
                        </Text>
                      </View>
                    </View>
                  </Card>
                </Pressable>
              );
            })
          )}
          {!directory.isLoading && (directory.data?.items ?? []).length === 0 ? (
            <Text variant="labelMd" color={colors.textMuted} style={{ textAlign: 'center', paddingVertical: spacing.md }}>
              No matching users.
            </Text>
          ) : null}
        </View>
      ) : null}

      {/* message */}
      <View style={{ gap: spacing.lg }}>
        <Input label="Title" value={title} onChangeText={setTitle} placeholder="e.g. Your property is secured" maxLength={120} />
        <Input
          label="Message"
          value={body}
          onChangeText={setBody}
          placeholder="What do you want to tell them?"
          multiline
          numberOfLines={5}
          maxLength={500}
          style={{ height: undefined, minHeight: 120, paddingTop: spacing.md, textAlignVertical: 'top' }}
        />
      </View>

      {/* delivery channels — admin picks in-app, push, or both */}
      <View style={{ gap: spacing.xs }}>
        <Text variant="labelMd" color={colors.textMuted}>Deliver via</Text>
        <View style={{ flexDirection: 'row', gap: spacing.md }}>
          <ChannelChip icon="notifications-outline" label="In-app alert" active={inApp} onPress={() => setInApp((v) => !v)} />
          <ChannelChip icon="phone-portrait-outline" label="Push to phone" active={push} onPress={() => setPush((v) => !v)} />
        </View>
        {!inApp && !push ? (
          <Text variant="labelMd" color={colors.error}>Pick at least one channel.</Text>
        ) : null}
      </View>

      <View style={{ flexDirection: 'row', alignItems: 'center', gap: spacing.sm, justifyContent: 'center' }}>
        <Ionicons name="megaphone-outline" size={16} color={colors.textMuted} />
        <Text variant="labelMd" color={colors.textMuted}>
          {reach > 0 ? `This will reach ${reach} ${reach === 1 ? 'person' : 'people'}.` : 'No recipients yet.'}
        </Text>
      </View>
    </Screen>
  );
}

function ChannelChip({ icon, label, active, onPress }: { icon: keyof typeof Ionicons.glyphMap; label: string; active: boolean; onPress: () => void }) {
  return (
    <Pressable onPress={onPress} style={{ flex: 1 }} accessibilityRole="button" accessibilityState={{ selected: active }}>
      <View
        style={{
          flexDirection: 'row',
          alignItems: 'center',
          gap: spacing.sm,
          padding: spacing.md,
          borderRadius: radius.lg,
          borderWidth: active ? 2 : 1,
          borderColor: active ? colors.primary : colors.border,
          backgroundColor: active ? colors.tealTint : colors.card,
        }}
      >
        <Ionicons name={active ? 'checkmark-circle' : icon} size={20} color={active ? colors.primary : colors.textMuted} />
        <Text variant="labelMd" weight="semibold" color={active ? colors.primary : colors.textMuted} style={{ flex: 1 }}>
          {label}
        </Text>
      </View>
    </Pressable>
  );
}
