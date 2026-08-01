import { Ionicons } from '@expo/vector-icons';
import { useQuery } from '@tanstack/react-query';
import { Image } from 'expo-image';
import { router } from 'expo-router';
import { useState } from 'react';
import { Pressable, View } from 'react-native';
import { Property, marketplaceApi, notificationsApi, propertiesApi } from '@/lib/api';
import { useAuth } from '@/lib/auth/AuthProvider';
import { PropertyCard } from '@/components/cards/PropertyCard';
import { AnimatedItem, Button, Card, EmptyState, ErrorState, Hero, ListScreen, ListSkeleton, Loading, NotificationBell, Screen, Text, formatCedis } from '@/components/ui';
import { colors, radius, spacing } from '@/theme';

const EMBLEM = require('@/assets/images/logo-emblem-clean.png');

export default function HomeTab() {
  const { user } = useAuth();
  const role = user?.role ?? 'OWNER';
  if (role === 'AGENT') return <AgentHome />;
  if (role === 'ADMIN') return <AdminHome />;
  return <OwnerHome />;
}

function timeGreeting(): string {
  const h = new Date().getHours();
  if (h < 12) return 'Good morning';
  if (h < 17) return 'Good afternoon';
  return 'Good evening';
}

/**
 * App identity bar — the emblem + wordmark sit at the top of every dashboard
 * (WhatsApp/Facebook style) so the app always announces itself on open.
 */
function BrandBar() {
  return (
    <View style={{ flexDirection: 'row', alignItems: 'center', gap: spacing.sm }}>
      <Image source={EMBLEM} style={{ width: 28, height: 28 }} contentFit="contain" />
      <Text variant="headlineSm" color={colors.primary}>
        AssetShield
      </Text>
      <View
        style={{
          paddingHorizontal: 6,
          paddingVertical: 1,
          borderRadius: radius.sm,
          backgroundColor: colors.tealTint,
        }}
      >
        <Text variant="labelMd" weight="semibold" color={colors.primary} style={{ fontSize: 10 }}>
          GH
        </Text>
      </View>
    </View>
  );
}

function Greeting({ subtitle }: { subtitle: string }) {
  const { user } = useAuth();
  const firstName = user?.fullName?.split(' ')[0] ?? 'there';
  return (
    <View style={{ gap: 2 }}>
      <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
        <BrandBar />
        <NotificationBell />
      </View>
      <Text variant="labelMd" color={colors.textMuted} style={{ marginTop: spacing.md }}>
        Akwaaba
      </Text>
      <Text variant="headlineLgMobile">
        {timeGreeting()}, {firstName}
      </Text>
      <Text variant="bodyMd" color={colors.textMuted}>
        {subtitle}
      </Text>
    </View>
  );
}

/**
 * Ghana-aware proactive banner: surfaces the freshest unread safety tip
 * (seasonal flood/fire risks from the tips engine) on the home screen.
 * Dismissible for the session; tapping opens the full tips feed.
 */
function RiskBanner() {
  const [dismissed, setDismissed] = useState(false);
  const tips = useQuery({ queryKey: ['tips-feed-banner'], queryFn: () => notificationsApi.feed({ size: 5 }) });
  const tip = (tips.data?.items ?? []).find((t) => !t.readAt) ?? tips.data?.items?.[0];
  if (dismissed || !tip) return null;

  // Dark ink on the amber banner (the warning color is the same amber family in
  // every theme, so a constant ink keeps AA contrast in light, dark and gold).
  const ink = '#2E2108';
  return (
    <Card style={{ backgroundColor: colors.warning }} onPress={() => router.push('/(app)/tips' as never)}>
      <View style={{ flexDirection: 'row', alignItems: 'flex-start', gap: spacing.md }}>
        <Ionicons name="warning" size={20} color={ink} />
        <View style={{ flex: 1, gap: 2 }}>
          <Text variant="labelMd" weight="semibold" color={ink}>
            {tip.category ? tip.category.replace(/_/g, ' ') : 'Safety tip'}
          </Text>
          <Text variant="labelMd" color={ink} numberOfLines={3}>
            {tip.tipText}
          </Text>
        </View>
        <Pressable accessibilityRole="button" accessibilityLabel="Dismiss tip" hitSlop={10} onPress={() => setDismissed(true)}>
          <Ionicons name="close" size={18} color={ink} />
        </Pressable>
      </View>
    </Card>
  );
}

/**
 * Documentation goes stale after the same 90 days the backend's re-documentation
 * sweep uses (REDOC_STALE_DAYS), so what the home screen says and the reminder
 * you eventually receive can never disagree.
 */
const STALE_DAYS = 90;

function isStale(lastDocumentedAt?: string): boolean {
  if (!lastDocumentedAt) return true;
  return (Date.now() - new Date(lastDocumentedAt).getTime()) / 86_400_000 > STALE_DAYS;
}

function plural(n: number, one: string, many: string): string {
  return `${n} ${n === 1 ? one : many}`;
}

/**
 * "Am I covered?" — a readiness statement, deliberately not a score. This app is
 * opened months apart, so the first thing an owner should learn on return is
 * whether their evidence still holds up, and which property to fix if it doesn't.
 * Tapping jumps straight to the property that needs the work.
 */
function ReadinessNote({ properties }: { properties: Property[] }) {
  const undocumented = properties.filter((p) => (p.assetCount ?? 0) === 0);
  const stale = properties.filter((p) => (p.assetCount ?? 0) > 0 && isStale(p.lastDocumentedAt));
  const target = undocumented[0] ?? stale[0];
  const needsWork = target !== undefined;

  const detail = needsWork
    ? [
        undocumented.length ? `${plural(undocumented.length, 'property has', 'properties have')} no assets yet` : null,
        stale.length ? `${plural(stale.length, 'record is', 'records are')} over ${STALE_DAYS} days old` : null,
      ]
        .filter(Boolean)
        .join('. ') + '.'
    : properties.length === 1
      ? 'Your property is documented and up to date.'
      : `All ${properties.length} properties are documented and up to date.`;

  const accent = needsWork ? colors.cta : colors.success;
  const chipBg = needsWork ? 'rgba(244,169,60,0.16)' : 'rgba(27,127,88,0.12)';

  return (
    <Card onPress={needsWork ? () => router.push(`/(app)/property/${target.id}` as never) : undefined}>
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: spacing.md }}>
        <View
          style={{
            width: 36,
            height: 36,
            borderRadius: 18,
            backgroundColor: chipBg,
            alignItems: 'center',
            justifyContent: 'center',
          }}
        >
          <Ionicons name={needsWork ? 'alert-circle' : 'shield-checkmark'} size={20} color={accent} />
        </View>
        <View style={{ flex: 1, gap: 2 }}>
          <Text variant="labelMd" weight="semibold">
            {needsWork ? 'Worth updating' : "You're covered"}
          </Text>
          <Text variant="labelMd" color={colors.textMuted}>
            {detail}
          </Text>
        </View>
        {needsWork ? <Ionicons name="chevron-forward" size={18} color={colors.textMuted} /> : null}
      </View>
    </Card>
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

      <RiskBanner />

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

      {items.length > 0 ? <ReadinessNote properties={items} /> : null}

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

/**
 * Calm status card for agent state (verification / subscription). A left icon
 * chip carries the color, keeping the card readable instead of a full-bleed
 * green/amber block with low-contrast text.
 */
function StatusPanel({
  icon,
  tone,
  eyebrow,
  title,
  body,
}: {
  icon: keyof typeof Ionicons.glyphMap;
  tone: 'active' | 'pending' | 'danger';
  eyebrow: string;
  title: string;
  body: string;
}) {
  const accent = tone === 'active' ? colors.success : tone === 'danger' ? colors.error : colors.cta;
  const chipBg =
    tone === 'active' ? 'rgba(27,127,88,0.12)' : tone === 'danger' ? 'rgba(186,26,26,0.12)' : 'rgba(244,169,60,0.16)';
  return (
    <Card>
      <View style={{ flexDirection: 'row', gap: spacing.md, alignItems: 'flex-start' }}>
        <View
          style={{
            width: 44,
            height: 44,
            borderRadius: 22,
            backgroundColor: chipBg,
            alignItems: 'center',
            justifyContent: 'center',
          }}
        >
          <Ionicons name={icon} size={22} color={accent} />
        </View>
        <View style={{ flex: 1, gap: 2 }}>
          <Text variant="labelMd" color={colors.textMuted} style={{ textTransform: 'uppercase', letterSpacing: 0.5 }}>
            {eyebrow}
          </Text>
          <Text variant="headlineSm">{title}</Text>
          <Text variant="bodyMd" color={colors.textMuted}>
            {body}
          </Text>
        </View>
      </View>
    </Card>
  );
}

/**
 * A tap-through count card for the agent hub. Turns the thin agent landing into
 * a "what needs me today" dashboard without adding any navigation.
 */
function AgentStat({ icon, value, label, onPress }: { icon: keyof typeof Ionicons.glyphMap; value: number; label: string; onPress: () => void }) {
  const active = value > 0;
  return (
    <Pressable onPress={onPress} style={{ flex: 1 }}>
      <Card style={{ gap: 4, minHeight: 96 }}>
        <Ionicons name={icon} size={20} color={active ? colors.primary : colors.textMuted} />
        <Text variant="headlineMd" color={active ? colors.text : colors.textMuted}>
          {value}
        </Text>
        <Text variant="labelMd" color={colors.textMuted} numberOfLines={2}>
          {label}
        </Text>
      </Card>
    </Pressable>
  );
}

/** The "what needs me today" row for a verified, subscribed agent. */
function AgentDashboard() {
  const leads = useQuery({ queryKey: ['leads'], queryFn: () => marketplaceApi.leads({ size: 1 }) });
  const shared = useQuery({ queryKey: ['shared-dossiers'], queryFn: () => marketplaceApi.sharedDossiers({ size: 1 }) });
  const quotes = useQuery({ queryKey: ['agent-quotes'], queryFn: () => marketplaceApi.myQuotes({ size: 50 }) });
  const pendingQuotes = (quotes.data?.items ?? []).filter((qt) => qt.status === 'PENDING').length;

  return (
    <View style={{ flexDirection: 'row', gap: spacing.md }}>
      <AgentStat icon="pricetags" value={leads.data?.totalElements ?? 0} label="Leads available" onPress={() => router.push('/(app)/(tabs)/market' as never)} />
      <AgentStat icon="folder-open" value={shared.data?.totalElements ?? 0} label="Dossiers to review" onPress={() => router.push('/(app)/(tabs)/activity' as never)} />
      <AgentStat icon="cash" value={pendingQuotes} label="Quotes awaiting reply" onPress={() => router.push('/(app)/(tabs)/activity' as never)} />
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
  const unlocked = verified && agent.subscription?.status === 'ACTIVE';

  return (
    <Screen refreshing={q.isRefetching} onRefresh={q.refetch}>
      <Greeting subtitle={agent.insurerName ? `${agent.insurerName} agent` : 'Insurance agent'} />

      {unlocked ? (
        // Active agent: the dashboard IS the status. A slim manage link keeps the
        // page focused on the work rather than repeating an "Active" banner.
        <>
          <AgentDashboard />
          <Button title="Manage subscription" variant="secondary" onPress={() => router.push('/(app)/subscription' as never)} />
        </>
      ) : (
        <>
          {!verified ? (
            <StatusPanel
              icon={verificationStatus === 'REJECTED' ? 'close-circle' : 'hourglass'}
              tone={verificationStatus === 'REJECTED' ? 'danger' : 'pending'}
              eyebrow="Agent account"
              title={verificationStatus === 'REJECTED' ? 'Application not approved' : 'Awaiting verification'}
              body={
                verificationStatus === 'REJECTED'
                  ? agent.rejectionReason ?? 'Please contact support for details.'
                  : 'An admin is reviewing your NIC licence. You can browse leads once approved.'
              }
            />
          ) : (
            <StatusPanel
              icon="lock-closed"
              tone="pending"
              eyebrow="Subscription"
              title="Not subscribed"
              body="Subscribe to unlock owner leads and receive shared dossiers."
            />
          )}
          <View style={{ gap: spacing.md }}>
            <Button title="Browse leads" disabled={!verified} onPress={() => router.push('/(app)/(tabs)/market' as never)} />
            <Button
              title="Subscribe"
              variant="primary"
              disabled={!verified}
              onPress={() => router.push('/(app)/subscription' as never)}
            />
          </View>
        </>
      )}
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
