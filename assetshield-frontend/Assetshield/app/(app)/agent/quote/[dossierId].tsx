import { useQueryClient } from '@tanstack/react-query';
import { router, useLocalSearchParams } from 'expo-router';
import { useState } from 'react';
import { Alert, View } from 'react-native';
import { isApiError, marketplaceApi } from '@/lib/api';
import { Button, Header, Input, Screen, Text, formatCedis, useToast } from '@/components/ui';
import { colors, spacing } from '@/theme';

/** CONSENT (beat 4, agent): issue a quote against a shared dossier. Stitch: "Issue Quote". */
export default function IssueQuote() {
  const { dossierId } = useLocalSearchParams<{ dossierId: string }>();
  const qc = useQueryClient();
  const { show } = useToast();
  const [coverage, setCoverage] = useState('');
  const [premium, setPremium] = useState('');
  const [term, setTerm] = useState('12');
  const [loading, setLoading] = useState(false);

  const submit = async () => {
    setLoading(true);
    try {
      await marketplaceApi.sendQuote(dossierId!, {
        coverageAmount: Number(coverage),
        premium: Number(premium),
        termMonths: Number(term) || 12,
      });
      qc.invalidateQueries({ queryKey: ['agent-quotes'] });
      show('Quote sent');
      router.back();
    } catch (e) {
      Alert.alert('Could not send', isApiError(e) ? e.message : 'Try again.');
    } finally {
      setLoading(false);
    }
  };

  // mirrors backend QuoteCreateRequest: amounts > 0, term 1-60 months
  const termN = Number(term) || 0;
  const termValid = termN >= 1 && termN <= 60;
  const valid = Number(coverage) > 0 && Number(premium) > 0 && termValid;

  return (
    <Screen footer={<Button title="Send quote" loading={loading} disabled={!valid} onPress={submit} />}>
      <Header title="Issue quote" />
      <View style={{ gap: spacing.lg }}>
        <Input label="Coverage amount (₵)" value={coverage} onChangeText={setCoverage} keyboardType="numeric" placeholder="40000" />
        <Input label="Premium per month (₵)" value={premium} onChangeText={setPremium} keyboardType="numeric" placeholder="120" />
        <Input
          label="Term (months)"
          value={term}
          onChangeText={setTerm}
          keyboardType="numeric"
          placeholder="12"
          error={term && !termValid ? 'Term must be between 1 and 60 months.' : undefined}
        />
      </View>
      {valid ? (
        <Text variant="labelMd" color={colors.textMuted}>
          Offering {formatCedis(Number(coverage))} cover at {formatCedis(Number(premium))}/mo for {Number(term) || 12} months.
        </Text>
      ) : null}
    </Screen>
  );
}
