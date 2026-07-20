import { useQuery } from '@tanstack/react-query';
import { router } from 'expo-router';
import { Pressable, View } from 'react-native';
import { usersApi } from '@/lib/api';
import {
  Card,
  EmptyState,
  ErrorState,
  Header,
  Hero,
  Loading,
  Screen,
  SectionHeader,
  Text,
  formatCedis,
} from '@/components/ui';
import { colors, spacing } from '@/theme';

const BAR_COLORS = ['#0E5A52', '#F4A93C', '#3FA392', '#8A8F8D', '#C25E4C', '#6B7AA1'];

function titleCase(v: string): string {
  return v.split('_').map((w) => w[0] + w.slice(1).toLowerCase()).join(' ');
}

/** Portfolio analytics — totals and breakdowns across every property. */
export default function Analytics() {
  const q = useQuery({ queryKey: ['asset-analytics'], queryFn: () => usersApi.assetAnalytics() });

  if (q.isLoading) return <Loading />;
  if (q.isError) return <ErrorState onRetry={() => q.refetch()} />;
  const a = q.data!;

  if (a.propertyCount === 0) {
    return (
      <Screen>
        <Header title="Analytics" />
        <EmptyState
          icon="bar-chart-outline"
          title="Nothing to chart yet"
          body="Add a property and document your first assets to see your portfolio here."
          actionLabel="New property"
          onAction={() => router.push('/(app)/property/new' as never)}
        />
      </Screen>
    );
  }

  const maxCategory = Math.max(...a.byCategory.map((c) => c.value), 1);

  return (
    <Screen>
      <Header title="Analytics" />

      <Hero>
        <Text variant="labelMd" color={colors.tealMuted}>
          Total protected value
        </Text>
        <Text variant="currencyDisplay" color={colors.onPrimary}>
          {formatCedis(a.totalValue)}
        </Text>
        <Text variant="labelMd" color={colors.tealMuted}>
          {a.assetCount} assets across {a.propertyCount} {a.propertyCount === 1 ? 'property' : 'properties'}
        </Text>
      </Hero>

      <SectionHeader title="Value by category" />
      <Card>
        <View style={{ gap: spacing.md }}>
          {a.byCategory.map((c, i) => (
            <View key={c.category} style={{ gap: 4 }}>
              <View style={{ flexDirection: 'row', justifyContent: 'space-between' }}>
                <Text variant="labelMd" color={colors.textMuted}>
                  {titleCase(c.category)} · {c.count}
                </Text>
                <Text variant="labelMd" weight="semibold">
                  {formatCedis(c.value)}
                </Text>
              </View>
              <View style={{ height: 8, borderRadius: 4, backgroundColor: colors.border, overflow: 'hidden' }}>
                <View
                  style={{
                    width: `${Math.max(4, Math.round((c.value / maxCategory) * 100))}%`,
                    height: '100%',
                    borderRadius: 4,
                    backgroundColor: BAR_COLORS[i % BAR_COLORS.length],
                  }}
                />
              </View>
            </View>
          ))}
        </View>
      </Card>

      <SectionHeader title="By property" />
      <View style={{ gap: spacing.md }}>
        {a.byProperty.map((p) => (
          <Pressable key={p.propertyId} onPress={() => router.push(`/(app)/property/${p.propertyId}` as never)}>
            <Card>
              <View style={{ flexDirection: 'row', alignItems: 'center' }}>
                <View style={{ flex: 1 }}>
                  <Text variant="bodyMd" weight="semibold">
                    {p.name}
                  </Text>
                  <Text variant="labelMd" color={colors.textMuted}>
                    {p.assetCount} {p.assetCount === 1 ? 'asset' : 'assets'}
                  </Text>
                </View>
                <Text variant="bodyMd" weight="semibold">
                  {formatCedis(p.totalValue)}
                </Text>
              </View>
            </Card>
          </Pressable>
        ))}
      </View>
    </Screen>
  );
}
