import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { router, useLocalSearchParams } from 'expo-router';
import { useEffect, useState } from 'react';
import { Alert, View } from 'react-native';
import { propertiesApi } from '@/lib/api';
import { Button, Header, Input, Loading, Screen } from '@/components/ui';
import { spacing } from '@/theme';

export default function EditProperty() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const propertyId = id!;
  const qc = useQueryClient();
  const q = useQuery({ queryKey: ['property', propertyId], queryFn: () => propertiesApi.get(propertyId) });

  const [name, setName] = useState('');
  const [locality, setLocality] = useState('');

  useEffect(() => {
    if (q.data) {
      setName(q.data.name);
      setLocality(q.data.locality ?? '');
    }
  }, [q.data]);

  const save = useMutation({
    mutationFn: () => propertiesApi.update(propertyId, { name, locality }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['property', propertyId] });
      qc.invalidateQueries({ queryKey: ['properties'] });
      router.back();
    },
    onError: (e: any) => Alert.alert('Could not save', e?.message ?? 'Try again.'),
  });

  const remove = () =>
    Alert.alert('Delete property?', 'This soft-deletes the property and its assets.', [
      { text: 'Cancel', style: 'cancel' },
      {
        text: 'Delete',
        style: 'destructive',
        onPress: async () => {
          try {
            await propertiesApi.remove(propertyId);
            qc.invalidateQueries({ queryKey: ['properties'] });
            router.replace('/(app)/(tabs)/properties' as never);
          } catch (e: any) {
            Alert.alert('Could not delete', e?.message ?? 'Try again.');
          }
        },
      },
    ]);

  if (q.isLoading) return <Loading />;

  return (
    <Screen>
      <Header title="Edit property" />
      <View style={{ gap: spacing.lg }}>
        <Input label="Property name" value={name} onChangeText={setName} />
        <Input label="Locality" value={locality} onChangeText={setLocality} />
      </View>
      <View style={{ gap: spacing.md, marginTop: spacing.md }}>
        <Button title="Save changes" loading={save.isPending} disabled={!name.trim()} onPress={() => save.mutate()} />
        <Button title="Delete property" variant="danger" onPress={remove} />
      </View>
    </Screen>
  );
}
