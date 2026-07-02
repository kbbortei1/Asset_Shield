import { Ionicons } from '@expo/vector-icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { router, useLocalSearchParams } from 'expo-router';
import { Pressable, Switch, View } from 'react-native';
import { propertiesApi } from '@/lib/api';
import {
  Button,
  Card,
  EmptyState,
  ErrorState,
  Header,
  Hero,
  ListScreen,
  Loading,
  RemoteImage,
  SectionHeader,
  Text,
  VerifiedBadge,
  ValuePill,
  formatCedis,
  useToast,
} from '@/components/ui';
import { colors, spacing } from '@/theme';

/** Property dashboard — the hub for documenting, reporting and managing a property. */
export default function PropertyDetail() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const propertyId = id!;
  const qc = useQueryClient();

  const property = useQuery({ queryKey: ['property', propertyId], queryFn: () => propertiesApi.get(propertyId) });
  const assets = useQuery({ queryKey: ['assets', propertyId], queryFn: () => propertiesApi.listAssets(propertyId, { size: 100 }) });

  const { show } = useToast();
  const optin = useMutation({
    mutationFn: (open: boolean) => propertiesApi.setOffersOptin(propertyId, open),
    onSuccess: (_d, open) => {
      qc.invalidateQueries({ queryKey: ['property', propertyId] });
      show(open ? 'Open to insurance offers' : 'No longer open to offers');
    },
  });

  if (property.isLoading) return <Loading />;
  if (property.isError) return <ErrorState onRetry={() => property.refetch()} />;
  const p = property.data!;
  const isOwner = p.myAccess === 'OWNER';
  const items = assets.data?.items ?? [];
  const totalValue = p.dashboard?.totalEstimatedValue ?? p.totalEstimatedValue ?? items.reduce((s, a) => s + (a.estimatedValue ?? 0), 0);
  const assetCount = p.dashboard?.assetCount ?? p.assetCount ?? items.length;

  const header = (
    <View style={{ gap: spacing.lg }}>
      <Header title={p.name} right={isOwner ? <Pressable onPress={() => router.push(`/(app)/property/${propertyId}/edit` as never)}><Ionicons name="create-outline" size={22} color={colors.primary} /></Pressable> : undefined} />

      <Hero>
        <Text variant="labelMd" color={colors.tealMuted}>
          Protected value · {p.locality ?? p.type}
        </Text>
        <Text variant="currencyDisplay" color={colors.onPrimary}>
          {formatCedis(totalValue)}
        </Text>
        <Text variant="labelMd" color={colors.tealMuted}>
          {assetCount} assets documented
        </Text>
      </Hero>

      <View style={{ flexDirection: 'row', gap: spacing.md }}>
        <Button title="Capture asset" onPress={() => router.push(`/(app)/property/${propertyId}/capture` as never)} />
        <Button title="Report damage" variant="secondary" onPress={() => router.push(`/(app)/damage/new?propertyId=${propertyId}` as never)} />
      </View>

      <View style={{ flexDirection: 'row', gap: spacing.md }}>
        <QuickLink icon="people-outline" label="Household" onPress={() => router.push(`/(app)/property/${propertyId}/invite` as never)} />
        <QuickLink icon="bulb-outline" label="Safety tips" onPress={() => router.push(`/(app)/property/${propertyId}/tips` as never)} />
        <QuickLink icon="alert-circle-outline" label="Reports" onPress={() => router.push(`/(app)/damage/list?propertyId=${propertyId}` as never)} />
      </View>

      {isOwner ? (
        <Card>
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: spacing.md }}>
            <Ionicons name="megaphone-outline" size={22} color={colors.primary} />
            <View style={{ flex: 1 }}>
              <Text variant="bodyMd" weight="semibold">
                Open to insurance offers
              </Text>
              <Text variant="labelMd" color={colors.textMuted}>
                Lets verified agents discover this property as a lead.
              </Text>
            </View>
            <Switch
              value={!!p.openToOffers}
              onValueChange={(v) => optin.mutate(v)}
              trackColor={{ true: colors.primary, false: colors.border }}
              thumbColor={colors.white}
            />
          </View>
        </Card>
      ) : null}

      <SectionHeader
        title="Documented assets"
        actionLabel="Capture"
        onAction={() => router.push(`/(app)/property/${propertyId}/capture` as never)}
      />
    </View>
  );

  return (
    <ListScreen
      data={items}
      numColumns={2}
      columnWrapperStyle={{ gap: spacing.md }}
      keyExtractor={(a) => a.id}
      refreshing={assets.isRefetching}
      onRefresh={() => { property.refetch(); assets.refetch(); }}
      header={header}
      renderItem={({ item: a }) => (
        <Pressable onPress={() => router.push(`/(app)/asset/${a.id}` as never)} style={{ flex: 1 }}>
          <Card padded={false} style={{ overflow: 'hidden' }}>
            <RemoteImage uri={a.photoUrl} height={120} />
            <View style={{ padding: spacing.md, gap: spacing.xs }}>
              <Text variant="labelMd" numberOfLines={1} weight="semibold">
                {a.description}
              </Text>
              {typeof a.estimatedValue === 'number' ? <ValuePill amount={a.estimatedValue} /> : null}
              <VerifiedBadge hash={a.sha256Hash} />
            </View>
          </Card>
        </Pressable>
      )}
      empty={
        assets.isLoading ? (
          <Loading />
        ) : (
          <EmptyState
            icon="cube-outline"
            title="No assets yet"
            body="Capture your first asset to build a tamper-evident record."
            actionLabel="Capture asset"
            onAction={() => router.push(`/(app)/property/${propertyId}/capture` as never)}
          />
        )
      }
    />
  );
}

function QuickLink({ icon, label, onPress }: { icon: keyof typeof Ionicons.glyphMap; label: string; onPress: () => void }) {
  return (
    <Pressable onPress={onPress} style={{ flex: 1 }}>
      <Card padded style={{ alignItems: 'center', paddingVertical: spacing.md }}>
        <Ionicons name={icon} size={24} color={colors.primary} />
        <Text variant="labelMd" color={colors.textMuted} style={{ marginTop: 4 }}>
          {label}
        </Text>
      </Card>
    </Pressable>
  );
}
