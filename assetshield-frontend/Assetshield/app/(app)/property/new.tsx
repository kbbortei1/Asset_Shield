import { Ionicons } from '@expo/vector-icons';
import { useQueryClient } from '@tanstack/react-query';
import { router } from 'expo-router';
import { useState } from 'react';
import { Alert, Pressable, View } from 'react-native';
import { isApiError, propertiesApi, PropertyType } from '@/lib/api';
import { getCurrentCoords } from '@/lib/media/capture';
import { Button, Card, Header, Input, Screen, Text } from '@/components/ui';
import { colors, radius, spacing } from '@/theme';

/** DOCUMENT (beat 1): create a property. */
export default function NewProperty() {
  const qc = useQueryClient();
  const [name, setName] = useState('');
  const [type, setType] = useState<PropertyType>('RESIDENTIAL');
  const [locality, setLocality] = useState('');
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(false);

  const submit = async () => {
    setErrors({});
    setLoading(true);
    try {
      const coords = await getCurrentCoords();
      const property = await propertiesApi.create({ name, type, locality, ...coords });
      qc.invalidateQueries({ queryKey: ['properties'] });
      router.replace(`/(app)/property/${property.id}` as never);
    } catch (e) {
      if (isApiError(e)) {
        if (e.code === 'FREE_TIER_LIMIT') {
          Alert.alert('Upgrade to PRO', 'The free plan allows one property. Upgrade to add more.', [
            { text: 'Not now', style: 'cancel' },
            { text: 'See PRO', onPress: () => router.push('/(app)/subscription' as never) },
          ]);
        } else if (e.fieldErrors) {
          setErrors(e.fieldErrors);
        } else {
          Alert.alert('Could not create', e.message);
        }
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <Screen>
      <Header title="Add property" />
      <View style={{ gap: spacing.lg }}>
        <Input label="Property name" value={name} onChangeText={setName} placeholder="e.g. Ama's Fabrics" error={errors.name} />

        <View style={{ gap: spacing.xs }}>
          <Text variant="labelMd" color={colors.textMuted}>
            Type
          </Text>
          <View style={{ flexDirection: 'row', gap: spacing.md }}>
            <TypeOption icon="home" label="Residential" active={type === 'RESIDENTIAL'} onPress={() => setType('RESIDENTIAL')} />
            <TypeOption icon="storefront" label="Commercial" active={type === 'COMMERCIAL'} onPress={() => setType('COMMERCIAL')} />
          </View>
        </View>

        <Input label="Locality" value={locality} onChangeText={setLocality} placeholder="e.g. Kantamanto" error={errors.locality} />

        <Card style={{ backgroundColor: colors.tealTint }}>
          <View style={{ flexDirection: 'row', gap: spacing.sm, alignItems: 'center' }}>
            <Ionicons name="location" size={18} color={colors.primary} />
            <Text variant="labelMd" color={colors.primary} style={{ flex: 1 }}>
              We'll tag this property with your current GPS location to help pair evidence later.
            </Text>
          </View>
        </Card>
      </View>

      <Button title="Create property" loading={loading} disabled={!name.trim()} onPress={submit} />
    </Screen>
  );
}

function TypeOption({
  icon,
  label,
  active,
  onPress,
}: {
  icon: keyof typeof Ionicons.glyphMap;
  label: string;
  active: boolean;
  onPress: () => void;
}) {
  return (
    <Pressable
      onPress={onPress}
      style={{
        flex: 1,
        height: 88,
        borderRadius: radius.md,
        borderWidth: active ? 2 : 1,
        borderColor: active ? colors.primary : colors.border,
        backgroundColor: active ? colors.tealTint : colors.card,
        alignItems: 'center',
        justifyContent: 'center',
        gap: spacing.xs,
      }}
    >
      <Ionicons name={icon} size={26} color={active ? colors.primary : colors.textMuted} />
      <Text variant="labelMd" color={active ? colors.primary : colors.textMuted}>
        {label}
      </Text>
    </Pressable>
  );
}
