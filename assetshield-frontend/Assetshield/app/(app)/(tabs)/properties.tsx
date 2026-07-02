import { Ionicons } from '@expo/vector-icons';
import { useQuery } from '@tanstack/react-query';
import { router } from 'expo-router';
import { View } from 'react-native';
import { propertiesApi } from '@/lib/api';
import { PropertyCard } from '@/components/cards/PropertyCard';
import { Button, Card, EmptyState, ErrorState, ListScreen, ListSkeleton, Screen, Text } from '@/components/ui';
import { colors, spacing } from '@/theme';

export default function PropertiesTab() {
  const q = useQuery({ queryKey: ['properties'], queryFn: () => propertiesApi.list({ size: 50 }) });
  const inv = useQuery({ queryKey: ['invitations'], queryFn: () => propertiesApi.myInvitations() });
  const pendingInvites = (inv.data ?? []).length;

  const header = (
    <View style={{ gap: spacing.lg }}>
      <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
        <Text variant="headlineLgMobile">Properties</Text>
        <Button title="Add" fullWidth={false} variant="secondary" onPress={() => router.push('/(app)/property/new' as never)} />
      </View>

      {pendingInvites > 0 ? (
        <Card onPress={() => router.push('/(app)/invitations' as never)} style={{ backgroundColor: colors.tealTint }}>
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: spacing.md }}>
            <Ionicons name="mail-unread" size={22} color={colors.primary} />
            <Text variant="bodyMd" color={colors.primary} style={{ flex: 1 }}>
              You have {pendingInvites} household invitation{pendingInvites === 1 ? '' : 's'}
            </Text>
            <Ionicons name="chevron-forward" size={18} color={colors.primary} />
          </View>
        </Card>
      ) : null}
    </View>
  );

  if (q.isLoading)
    return (
      <Screen>
        {header}
        <ListSkeleton />
      </Screen>
    );
  if (q.isError) return <ErrorState onRetry={() => q.refetch()} />;

  const items = q.data?.items ?? [];

  return (
    <ListScreen
      data={items}
      keyExtractor={(p) => p.id}
      refreshing={q.isRefetching}
      onRefresh={q.refetch}
      header={header}
      renderItem={({ item }) => (
        <PropertyCard property={item} onPress={() => router.push(`/(app)/property/${item.id}` as never)} />
      )}
      empty={
        <EmptyState
          icon="business-outline"
          title="No properties yet"
          body="Add your first property to begin documenting assets."
          actionLabel="Add a property"
          onAction={() => router.push('/(app)/property/new' as never)}
        />
      }
    />
  );
}
