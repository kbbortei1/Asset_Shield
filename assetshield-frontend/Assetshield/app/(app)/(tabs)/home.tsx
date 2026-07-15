import { Ionicons } from '@expo/vector-icons';
import { useQuery } from '@tanstack/react-query';
import { router } from 'expo-router';
import { View } from 'react-native';
import { marketplaceApi, propertiesApi } from '@/lib/api';
import { useAuth } from '@/lib/auth/AuthProvider';
import { PropertyCard } from '@/components/cards/PropertyCard';
import { AnimatedItem, Button, Card, EmptyState, ErrorState, Hero, ListScreen, ListSkeleton, Loading, Screen, Text, formatCedis } from '@/components/ui';
import { colors, spacing } from '@/theme';

export default function HomeTab() {
  const { user } = useAuth();
  const role = user?.role ?? 'OWNER';
  if (role === 'AGENT') return <AgentHome />;
  if (role === 'ADMIN') return <AdminHome />;
  return <OwnerHome />;
}

function Greeting({ subtitle }: { subtitle: string }) {
  const { user } = useAuth();
  const firstName = user?.fullName?.split(' ')[0] ?? 'there';
  return (
    <View style={{ gap: 2 }}>
      <Text variant="labelMd" color={colors.textMuted}>
        Welcome back
      </Text>
      <Text variant="headlineLgMobile">Hi, {firstName}</Text>
      <Text variant="bodyMd" color={colors.textMuted}>
        {subtitle}
      </Text>
    </View>
  );
}

/** Stitch: "Owner Home — Protection Score" / "Dashboard". */
function OwnerHome() {
  const q = useQuery({ queryKey: ['properties'], queryFn: () => propertiesApi.list({ size: 50 }) });

  if (q.isLoading)
    return (
      <Screen>
        <ListSkeleton hero count={3} />
      </Screen>
    );
  if (q.isError) return <ErrorState onRetry={() => q.refetch()} />;

  const items = q.data?.items ?? [];
  const totalValue = items.reduce((s, p) => s + (p.totalEstimatedValue ?? 0), 0);
  const totalAssets = items.reduce((s, p) => s + (p.assetCount ?? 0), 0);

  const header = (
    <View style={{ gap: spacing.lg }}>
      <Greeting subtitle="Here's what you're protecting." />

      <Hero>
        <Text variant="labelMd" color={colors.tealMuted}>
          Total protected value
        </Text>
        <Text variant="currencyDisplay" color={colors.onPrimary}>
          {formatCedis(totalValue)}
        </Text>
        <View style={{ flexDirection: 'row', gap: spacing.xl, marginTop: spacing.sm }}>
          <Stat label="Properties" value={String(items.length)} />
          <Stat label="Assets documented" value={String(totalAssets)} />
        </View>
      </Hero>

      <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
        <Text variant="headlineSm">Your properties</Text>
        <Button title="Add" fullWidth={false} variant="secondary" onPress={() => router.push('/(app)/property/new' as never)} />
      </View>
    </View>
  );

  return (
    <ListScreen
      data={items}
      keyExtractor={(p) => p.id}
      refreshing={q.isRefetching}
      onRefresh={q.refetch}
      header={header}
      renderItem={({ item, index }) => (
        <AnimatedItem index={index}>
          <PropertyCard property={item} onPress={() => router.push(`/(app)/property/${item.id}` as never)} />
        </AnimatedItem>
      )}
      empty={
        <EmptyState
          icon="home-outline"
          title="No properties yet"
          body="Add your home or business to start documenting your assets."
          actionLabel="Add a property"
          onAction={() => router.push('/(app)/property/new' as never)}
        />
      }
    />
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <View>
      <Text variant="headlineMd" color={colors.onPrimary}>
        {value}
      </Text>
      <Text variant="labelMd" color={colors.tealMuted}>
        {label}
      </Text>
    </View>
  );
}

/** Stitch: agent landing (from "Agent Registration & Verification" / leads). */
function AgentHome() {
  const q = useQuery({ queryKey: ['agent', 'me'], queryFn: () => marketplaceApi.agentMe() });

  if (q.isLoading) return <Loading />;
  if (q.isError) return <ErrorState onRetry={() => q.refetch()} />;
  const agent = q.data!;
  const verificationStatus = agent.verificationStatus ?? agent.status;
  const verified = verificationStatus === 'VERIFIED';

  return (
    <Screen refreshing={q.isRefetching} onRefresh={q.refetch}>
      <Greeting subtitle={agent.insurerName ? `${agent.insurerName} agent` : 'Insurance agent'} />

      {!verified ? (
        <Card style={{ backgroundColor: verificationStatus === 'REJECTED' ? colors.error : colors.warning }}>
          <Text variant="headlineSm" color={colors.white}>
            {verificationStatus === 'REJECTED' ? 'Application not approved' : 'Awaiting verification'}
          </Text>
          <Text variant="bodyMd" color={colors.white}>
            {verificationStatus === 'REJECTED'
              ? agent.rejectionReason ?? 'Please contact support for details.'
              : 'An admin is reviewing your NIC licence. You can browse once approved.'}
          </Text>
        </Card>
      ) : (
        <Card style={{ backgroundColor: colors.tealTint }}>
          <Text variant="labelMd" color={colors.primary}>
            Subscription
          </Text>
          <Text variant="headlineSm" color={colors.primary}>
            {agent.subscription?.status === 'ACTIVE' ? 'Active' : 'Inactive. Subscribe to access leads.'}
          </Text>
        </Card>
      )}

      <View style={{ gap: spacing.md }}>
        <Button title="Browse leads" disabled={!verified} onPress={() => router.push('/(app)/(tabs)/market' as never)} />
        <Button title="Manage subscription" variant="secondary" onPress={() => router.push('/(app)/subscription' as never)} />
      </View>
    </Screen>
  );
}

function AdminHome() {
  const q = useQuery({ queryKey: ['admin', 'agents', 'PENDING_VERIFICATION'], queryFn: () => marketplaceApi.adminAgents('PENDING_VERIFICATION', { size: 1 }) });
  const pending = q.data?.totalElements ?? 0;

  return (
    <Screen refreshing={q.isRefetching} onRefresh={q.refetch}>
      <Greeting subtitle="Administrator console." />
      <Hero onPress={() => router.push('/(app)/(tabs)/market' as never)}>
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: spacing.lg }}>
          <Ionicons name="shield-checkmark" size={32} color={colors.cta} />
          <View style={{ flex: 1 }}>
            <Text variant="headlineSm" color={colors.onPrimary}>
              Agent verification queue
            </Text>
            <Text variant="bodyMd" color={colors.tealMuted}>
              {pending} awaiting review
            </Text>
          </View>
          <Ionicons name="chevron-forward" size={22} color={colors.onPrimary} />
        </View>
      </Hero>
      <Button title="Create an admin" variant="secondary" onPress={() => router.push('/(app)/admin/new' as never)} />
    </Screen>
  );
}
