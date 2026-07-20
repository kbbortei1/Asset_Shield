import { Ionicons } from '@expo/vector-icons';
import { useQuery } from '@tanstack/react-query';
import { router, useLocalSearchParams } from 'expo-router';
import { Pressable, View } from 'react-native';
import { TimelineEvent, TimelineEventType, propertiesApi } from '@/lib/api';
import { Card, EmptyState, ErrorState, Header, ListScreen, Loading, Text } from '@/components/ui';
import { colors, spacing } from '@/theme';

const EVENT_META: Record<TimelineEventType, { icon: keyof typeof Ionicons.glyphMap; title: string; color: string }> = {
  PROPERTY_CREATED: { icon: 'home', title: 'Property created', color: '#0E5A52' },
  ASSET_ADDED: { icon: 'cube', title: 'Asset documented', color: '#3FA392' },
  RECEIPT_ADDED: { icon: 'receipt', title: 'Receipt attached', color: '#F4A93C' },
  ASSET_REMOVED: { icon: 'trash', title: 'Asset removed', color: '#8A8F8D' },
};

/** Derived history of everything that happened on a property, newest first. */
export default function PropertyTimeline() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const propertyId = id!;
  const q = useQuery({
    queryKey: ['timeline', propertyId],
    queryFn: () => propertiesApi.timeline(propertyId),
  });

  if (q.isLoading) return <Loading />;
  if (q.isError) return <ErrorState onRetry={() => q.refetch()} />;
  const events = q.data ?? [];

  return (
    <ListScreen
      data={events}
      keyExtractor={(e, i) => `${e.type}-${e.at}-${e.assetId ?? 'p'}-${i}`}
      refreshing={q.isRefetching}
      onRefresh={() => q.refetch()}
      header={<Header title="History" />}
      renderItem={({ item }) => <EventRow event={item} />}
      empty={
        <EmptyState
          icon="time-outline"
          title="No history yet"
          body="Events appear here as you document assets and attach receipts."
        />
      }
    />
  );
}

function EventRow({ event }: { event: TimelineEvent }) {
  const meta = EVENT_META[event.type] ?? EVENT_META.ASSET_ADDED;
  const removed = event.type === 'ASSET_REMOVED';
  const body = (
    <Card>
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: spacing.md }}>
        <View
          style={{
            width: 40,
            height: 40,
            borderRadius: 20,
            backgroundColor: colors.tealTint,
            alignItems: 'center',
            justifyContent: 'center',
          }}
        >
          <Ionicons name={meta.icon} size={18} color={meta.color} />
        </View>
        <View style={{ flex: 1 }}>
          <Text variant="bodyMd" weight="semibold">
            {meta.title}
          </Text>
          <Text variant="labelMd" color={colors.textMuted} numberOfLines={1}>
            {event.label}
          </Text>
        </View>
        <Text variant="labelMd" color={colors.textMuted}>
          {new Date(event.at).toLocaleDateString()}
        </Text>
      </View>
    </Card>
  );
  // removed assets 404 on open — only link the ones that still exist
  if (event.assetId && !removed) {
    return <Pressable onPress={() => router.push(`/(app)/asset/${event.assetId}` as never)}>{body}</Pressable>;
  }
  return body;
}
