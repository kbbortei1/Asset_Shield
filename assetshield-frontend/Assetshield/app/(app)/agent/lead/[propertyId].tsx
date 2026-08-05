import { Ionicons } from '@expo/vector-icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { router, useLocalSearchParams } from 'expo-router';
import { View } from 'react-native';

import { isApiError, Lead, marketplaceApi } from '@/lib/api';
import { Button, Card, Header, Screen, Text, useToast, showAlert } from '@/components/ui';
import { colors, spacing } from '@/theme';

/** CONSENT (beat 4, agent): lead detail + express interest. Leads are 5 fields only. */
export default function LeadDetail() {
  const { propertyId } = useLocalSearchParams<{ propertyId: string }>();
  const qc = useQueryClient();
  const { show } = useToast();
  const leads = useQuery({ queryKey: ['leads'], queryFn: () => marketplaceApi.leads({ size: 50 }) });
  const lead: Lead | undefined = leads.data?.items.find((l) => l.propertyId === propertyId);

  const express = useMutation({
    mutationFn: () => marketplaceApi.expressInterest(propertyId!),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['agent-interests-mine'] });
      show('Interest sent — the owner will be notified');
      router.back();
    },
    onError: (e) => {
      if (isApiError(e)) {
        if (e.code === 'DUPLICATE_PENDING_INTEREST') showAlert('Already expressed', 'You’ve already expressed interest in this lead.');
        else if (e.code === 'RESOURCE_NOT_FOUND') showAlert('No longer available', 'This property is not open to offers.');
        else if (e.code === 'AGENT_NOT_VERIFIED') showAlert('Verification required', 'Your account must be verified first.');
        else if (e.code === 'SUBSCRIPTION_INACTIVE') showAlert('Subscription required', 'Subscribe to express interest.');
        else showAlert('Could not send', e.message);
      }
    },
  });

  return (
    <Screen>
      <Header title="Lead" />
      <Card>
        <View style={{ gap: spacing.sm }}>
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: spacing.md }}>
            <Ionicons name={lead?.propertyType === 'COMMERCIAL' ? 'storefront' : 'home'} size={28} color={colors.primary} />
            <View style={{ flex: 1 }}>
              <Text variant="headlineSm">{lead?.propertyName ?? 'Property'}</Text>
              <Text variant="labelMd" color={colors.textMuted}>
                {lead?.locality ?? ''}
              </Text>
            </View>
          </View>
          <Field label="Owner" value={lead?.ownerDisplayName ?? '-'} />
          <Field label="Type" value={lead?.propertyType ?? '-'} />
          <Field label="Locality" value={lead?.locality ?? '-'} />
        </View>
      </Card>

      <Card style={{ backgroundColor: colors.tealTint }}>
        <Text variant="labelMd" color={colors.primary}>
          For privacy, leads show only these details. Express interest, and if the owner accepts, they can share a verified dossier with you.
        </Text>
      </Card>

      <Button title="Express interest" loading={express.isPending} onPress={() => express.mutate()} />
    </Screen>
  );
}

function Field({ label, value }: { label: string; value: string }) {
  return (
    <View style={{ flexDirection: 'row', justifyContent: 'space-between' }}>
      <Text variant="labelMd" color={colors.textMuted}>
        {label}
      </Text>
      <Text variant="labelMd" weight="semibold">
        {value}
      </Text>
    </View>
  );
}
