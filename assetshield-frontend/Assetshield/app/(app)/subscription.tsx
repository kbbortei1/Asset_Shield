import { Ionicons } from '@expo/vector-icons';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useLocalSearchParams } from 'expo-router';
import { useState } from 'react';
import { Alert, View } from 'react-native';
import { marketplaceApi } from '@/lib/api';
import { useAuth } from '@/lib/auth/AuthProvider';
import { runCheckout } from '@/lib/payments/checkout';
import { Button, Card, ErrorState, Header, Loading, Screen, Text } from '@/components/ui';
import { colors, spacing } from '@/theme';

/** Owner PRO + agent subscription (shared payment flow). MISSING_DESIGN for owner; agent matches Stitch "Agent Subscription". */
export default function Subscription() {
  const { user } = useAuth();
  const isAgent = user?.role === 'AGENT';
  const qc = useQueryClient();
  const [paying, setPaying] = useState(false);
  useLocalSearchParams();

  const q = useQuery({
    queryKey: ['subscription', isAgent ? 'agent' : 'owner'],
    queryFn: () => (isAgent ? marketplaceApi.agentSubscription() : marketplaceApi.mySubscription()),
  });

  const subscribe = async () => {
    setPaying(true);
    try {
      const res = isAgent ? await marketplaceApi.subscribeAgent() : await marketplaceApi.buyPro();
      const outcome = await runCheckout(res); // subscription-init returns a flat PaymentHandle
      if (outcome === 'failed') Alert.alert('Payment failed', 'Please try again.');
      qc.invalidateQueries({ queryKey: ['subscription'] });
      qc.invalidateQueries({ queryKey: ['agent', 'me'] });
      qc.invalidateQueries({ queryKey: ['subscription', isAgent ? 'agent' : 'owner'] });
    } catch (e: any) {
      Alert.alert('Could not start payment', e?.message ?? 'Try again.');
    } finally {
      setPaying(false);
    }
  };

  if (q.isLoading) return <Loading />;
  if (q.isError) return <ErrorState onRetry={() => q.refetch()} />;
  const sub = q.data!;
  const active = sub.status === 'ACTIVE';

  const features = isAgent
    ? ['Unlock owner leads in your area', 'Receive shared dossiers', 'Send insurance quotes']
    : ['Document unlimited properties', 'Priority dossier generation', 'Marketplace offers from agents'];

  return (
    <Screen>
      <Header title={isAgent ? 'Subscription' : 'AssetShield PRO'} />

      <Card style={{ backgroundColor: active ? colors.success : colors.primary }}>
        <Text variant="labelMd" color={active ? colors.white : colors.tealMuted}>
          {isAgent ? 'Agent subscription' : 'Plan'}
        </Text>
        <Text variant="headlineMd" color={colors.white}>
          {active ? 'Active' : isAgent ? 'Not subscribed' : 'Free plan'}
        </Text>
        {sub.expiresAt ? (
          <Text variant="labelMd" color={colors.white}>
            Renews {new Date(sub.expiresAt).toLocaleDateString()}
          </Text>
        ) : null}
      </Card>

      <Card>
        <View style={{ gap: spacing.md }}>
          {features.map((f) => (
            <View key={f} style={{ flexDirection: 'row', gap: spacing.sm, alignItems: 'center' }}>
              <Ionicons name="checkmark-circle" size={20} color={colors.success} />
              <Text variant="bodyMd" style={{ flex: 1 }}>
                {f}
              </Text>
            </View>
          ))}
        </View>
      </Card>

      {!active ? (
        <Button title={isAgent ? 'Subscribe' : 'Upgrade to PRO'} loading={paying} onPress={subscribe} />
      ) : (
        <Text variant="labelMd" color={colors.textMuted} align="center">
          You're all set. Manage billing under your profile.
        </Text>
      )}
    </Screen>
  );
}
