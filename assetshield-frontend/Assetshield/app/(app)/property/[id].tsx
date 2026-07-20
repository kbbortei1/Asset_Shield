import { Ionicons } from '@expo/vector-icons';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { File, Paths } from 'expo-file-system';
import { router, useLocalSearchParams } from 'expo-router';
import * as Sharing from 'expo-sharing';
import { useEffect, useState } from 'react';
import { Alert, Pressable, Switch, View } from 'react-native';
import { Asset, AssetCategory, isApiError, propertiesApi } from '@/lib/api';
import {
  Button,
  Card,
  EmptyState,
  ErrorState,
  EvidencePhoto,
  Header,
  Hero,
  Input,
  ListScreen,
  Loading,
  SectionHeader,
  Text,
  VerifiedBadge,
  ValuePill,
  formatCedis,
  useToast,
} from '@/components/ui';
import { colors, radius, spacing } from '@/theme';

/** Sentinel row appended to the asset grid: the dashed "log new asset" tile. */
const ADD_TILE = '__add_asset__';
type GridItem = Asset | typeof ADD_TILE;

const BREAKDOWN_COLORS = ['#0E5A52', '#F4A93C', '#3FA392', '#8A8F8D'];

const CATEGORIES: AssetCategory[] = ['ELECTRONICS', 'FURNITURE', 'CLOTHING_STOCK', 'MACHINERY', 'DOCUMENTS', 'OTHER'];

function titleCase(v: string): string {
  return v.split('_').map((w) => w[0] + w.slice(1).toLowerCase()).join(' ');
}

/** Debounce a fast-changing value (search-as-you-type without query spam). */
function useDebounced<T>(value: T, ms: number): T {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const t = setTimeout(() => setDebounced(value), ms);
    return () => clearTimeout(t);
  }, [value, ms]);
  return debounced;
}

/** Property dashboard — the hub for documenting, reporting and managing a property. */
export default function PropertyDetail() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const propertyId = id!;
  const qc = useQueryClient();

  const [search, setSearch] = useState('');
  const [category, setCategory] = useState<AssetCategory | undefined>(undefined);
  const q = useDebounced(search.trim(), 300);
  const filtering = q.length > 0 || category !== undefined;

  const property = useQuery({ queryKey: ['property', propertyId], queryFn: () => propertiesApi.get(propertyId) });
  const assets = useQuery({
    queryKey: ['assets', propertyId, q, category ?? 'ALL'],
    queryFn: () => propertiesApi.listAssets(propertyId, { size: 100, q: q || undefined, category }),
    placeholderData: keepPreviousData,
  });

  const { show } = useToast();
  const [exporting, setExporting] = useState(false);
  const exportCsv = async () => {
    if (exporting) return;
    setExporting(true);
    try {
      const csv = await propertiesApi.exportAssetsCsv(propertyId);
      const file = new File(Paths.cache, `assetshield-assets-${propertyId.slice(0, 8)}.csv`);
      if (file.exists) file.delete();
      file.write(csv);
      if (await Sharing.isAvailableAsync()) {
        await Sharing.shareAsync(file.uri, { mimeType: 'text/csv', dialogTitle: 'Export assets' });
      } else {
        show('Sharing is not available on this device');
      }
    } catch (e) {
      Alert.alert('Could not export', isApiError(e) ? e.message : 'Please try again.');
    } finally {
      setExporting(false);
    }
  };
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
  const canExport = isOwner || p.myAccess === 'MEMBER_EXPORT';
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

      <View style={{ flexDirection: 'row', gap: spacing.md }}>
        <QuickLink icon="time-outline" label="History" onPress={() => router.push(`/(app)/property/${propertyId}/timeline` as never)} />
        <QuickLink icon="bar-chart-outline" label="Analytics" onPress={() => router.push('/(app)/analytics' as never)} />
        {canExport ? <QuickLink icon="download-outline" label={exporting ? 'Exporting…' : 'Export CSV'} onPress={exportCsv} /> : null}
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

      {items.length > 0 ? <ValueBreakdown assets={items} /> : null}

      <Card>
        <View style={{ gap: spacing.sm }}>
          <AuditRow label="Assets logged" value={String(assetCount)} />
          <AuditRow label="Tamper-evident photos" value={items.length > 0 ? '100% hashed' : '-'} good={items.length > 0} />
          <AuditRow
            label="Last documented"
            value={p.lastDocumentedAt ? new Date(p.lastDocumentedAt).toLocaleDateString() : 'Not yet'}
          />
        </View>
      </Card>

      <SectionHeader
        title="Documented assets"
        actionLabel="Capture"
        onAction={() => router.push(`/(app)/property/${propertyId}/capture` as never)}
      />

      <Input
        placeholder="Search assets by name…"
        value={search}
        onChangeText={setSearch}
        autoCapitalize="none"
        returnKeyType="search"
      />
      <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: spacing.sm }}>
        {CATEGORIES.map((c) => {
          const active = category === c;
          return (
            <Pressable
              key={c}
              onPress={() => setCategory(active ? undefined : c)}
              style={{
                paddingHorizontal: spacing.md,
                paddingVertical: spacing.xs,
                borderRadius: radius.xl,
                borderWidth: 1,
                borderColor: active ? colors.primary : colors.border,
                backgroundColor: active ? colors.primary : 'transparent',
              }}
            >
              <Text variant="labelMd" weight="semibold" color={active ? colors.onPrimary : colors.textMuted}>
                {titleCase(c)}
              </Text>
            </Pressable>
          );
        })}
      </View>
    </View>
  );

  // the dashed "add asset" tile only belongs to the unfiltered grid
  const gridData: GridItem[] = items.length > 0 ? (filtering ? items : [...items, ADD_TILE]) : [];

  return (
    <ListScreen
      data={gridData}
      numColumns={2}
      columnWrapperStyle={{ gap: spacing.md }}
      keyExtractor={(a) => (a === ADD_TILE ? ADD_TILE : a.id)}
      refreshing={assets.isRefetching}
      onRefresh={() => { property.refetch(); assets.refetch(); }}
      header={header}
      renderItem={({ item: a }) =>
        a === ADD_TILE ? (
          <Pressable
            onPress={() => router.push(`/(app)/property/${propertyId}/capture` as never)}
            style={{ flex: 1 }}
          >
            <View
              style={{
                minHeight: 180,
                borderRadius: radius.lg,
                borderWidth: 1.5,
                borderStyle: 'dashed',
                borderColor: colors.primary,
                alignItems: 'center',
                justifyContent: 'center',
                gap: spacing.sm,
                backgroundColor: colors.tealTint,
              }}
            >
              <Ionicons name="add-circle" size={30} color={colors.primary} />
              <Text variant="labelMd" weight="semibold" color={colors.primary}>
                Log new asset
              </Text>
              <Text variant="labelMd" color={colors.textMuted} style={{ fontSize: 10 }}>
                Secure & hashed
              </Text>
            </View>
          </Pressable>
        ) : (
          <Pressable onPress={() => router.push(`/(app)/asset/${a.id}` as never)} style={{ flex: 1 }}>
            <Card padded={false} style={{ overflow: 'hidden' }}>
              <EvidencePhoto
                uri={a.photoUrl}
                height={120}
                gpsLat={a.gpsLat}
                gpsLng={a.gpsLng}
                capturedAt={a.capturedAt}
                verified={a.sha256Hash}
              />
              <View style={{ padding: spacing.md, gap: spacing.xs }}>
                <Text variant="labelMd" numberOfLines={1} weight="semibold">
                  {a.description}
                </Text>
                {typeof a.estimatedValue === 'number' ? <ValuePill amount={a.estimatedValue} /> : null}
                <VerifiedBadge hash={a.sha256Hash} />
              </View>
            </Card>
          </Pressable>
        )
      }
      empty={
        assets.isLoading ? (
          <Loading />
        ) : filtering ? (
          <EmptyState
            icon="search-outline"
            title="No matching assets"
            body="Try a different search term or clear the category filter."
          />
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

function AuditRow({ label, value, good }: { label: string; value: string; good?: boolean }) {
  return (
    <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
      <Text variant="labelMd" color={colors.textMuted}>
        {label}
      </Text>
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: 4 }}>
        {good ? <Ionicons name="checkmark-circle" size={14} color={colors.success} /> : null}
        <Text variant="bodyMd" weight="semibold">
          {value}
        </Text>
      </View>
    </View>
  );
}

/** Stacked bar showing how the protected value splits across asset categories. */
function ValueBreakdown({ assets }: { assets: Asset[] }) {
  const byCategory = new Map<string, number>();
  for (const a of assets) {
    if (typeof a.estimatedValue !== 'number') continue;
    const key = a.category ?? 'OTHER';
    byCategory.set(key, (byCategory.get(key) ?? 0) + a.estimatedValue);
  }
  const entries = [...byCategory.entries()].sort((x, y) => y[1] - x[1]).slice(0, 4);
  const sum = entries.reduce((s, [, v]) => s + v, 0);
  if (sum <= 0) return null;

  return (
    <Card>
      <Text variant="labelMd" color={colors.textMuted} style={{ marginBottom: spacing.sm }}>
        Value by category
      </Text>
      <View style={{ flexDirection: 'row', height: 8, borderRadius: 4, overflow: 'hidden', marginBottom: spacing.md }}>
        {entries.map(([cat, v], i) => (
          <View key={cat} style={{ flex: v / sum, backgroundColor: BREAKDOWN_COLORS[i % BREAKDOWN_COLORS.length] }} />
        ))}
      </View>
      <View style={{ gap: spacing.xs }}>
        {entries.map(([cat, v], i) => (
          <View key={cat} style={{ flexDirection: 'row', alignItems: 'center', gap: spacing.sm }}>
            <View style={{ width: 8, height: 8, borderRadius: 2, backgroundColor: BREAKDOWN_COLORS[i % BREAKDOWN_COLORS.length] }} />
            <Text variant="labelMd" color={colors.textMuted} style={{ flex: 1 }}>
              {titleCase(cat)}
            </Text>
            <Text variant="labelMd" weight="semibold">
              {formatCedis(v)}
            </Text>
          </View>
        ))}
      </View>
    </Card>
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
