import { Ionicons } from '@expo/vector-icons';
import { useQueryClient } from '@tanstack/react-query';
import { router, useLocalSearchParams } from 'expo-router';
import { useState } from 'react';
import { Alert, Pressable, ScrollView } from 'react-native';
import { damageApi, DisasterType } from '@/lib/api';
import { Button, Header, Input, Screen, Text } from '@/components/ui';
import { colors, radius, spacing } from '@/theme';

const TYPES: { type: DisasterType; icon: keyof typeof Ionicons.glyphMap; label: string }[] = [
  { type: 'FIRE', icon: 'flame', label: 'Fire' },
  { type: 'FLOOD', icon: 'water', label: 'Flood' },
  { type: 'THEFT', icon: 'lock-open', label: 'Theft' },
  { type: 'STORM', icon: 'thunderstorm', label: 'Storm' },
  { type: 'OTHER', icon: 'ellipsis-horizontal', label: 'Other' },
];

/** DISASTER (beat 2): open a damage report. Stitch: "Report Damage - Select Type". */
export default function NewDamageReport() {
  const { propertyId } = useLocalSearchParams<{ propertyId: string }>();
  const qc = useQueryClient();
  const [type, setType] = useState<DisasterType>('FIRE');
  const [description, setDescription] = useState('');
  const [loading, setLoading] = useState(false);

  const submit = async () => {
    if (!propertyId) return;
    setLoading(true);
    try {
      const report = await damageApi.create(propertyId, {
        disasterType: type,
        description,
        occurredAt: new Date().toISOString(),
      });
      qc.invalidateQueries({ queryKey: ['reports', propertyId] });
      router.replace(`/(app)/damage/${report.id}` as never);
    } catch (e: any) {
      Alert.alert('Could not open report', e?.message ?? 'Try again.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Screen>
      <Header title="Report damage" />
      <Text variant="bodyMd" color={colors.textMuted}>
        What happened? You'll add photos next and pair them with your documented assets.
      </Text>

      <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ gap: spacing.md, paddingVertical: spacing.xs }}>
        {TYPES.map((t) => {
          const active = t.type === type;
          return (
            <Pressable
              key={t.type}
              onPress={() => setType(t.type)}
              style={{
                width: 96,
                height: 96,
                borderRadius: radius.lg,
                alignItems: 'center',
                justifyContent: 'center',
                gap: spacing.xs,
                backgroundColor: active ? colors.tealTint : colors.card,
                borderWidth: active ? 2 : 1,
                borderColor: active ? colors.primary : colors.border,
              }}
            >
              <Ionicons name={t.icon} size={28} color={active ? colors.primary : colors.textMuted} />
              <Text variant="labelMd" color={active ? colors.primary : colors.textMuted}>
                {t.label}
              </Text>
            </Pressable>
          );
        })}
      </ScrollView>

      <Input label="Describe what happened" value={description} onChangeText={setDescription} placeholder="e.g. Night fire at Kantamanto" multiline style={{ height: 120, paddingTop: spacing.md }} />

      <Button title="Start report" loading={loading} onPress={submit} />
    </Screen>
  );
}
