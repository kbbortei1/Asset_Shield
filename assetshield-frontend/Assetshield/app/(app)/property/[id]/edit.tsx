import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { router, useLocalSearchParams } from 'expo-router';
import { useEffect, useState } from 'react';
import { Alert, View } from 'react-native';
import { propertiesApi, usersApi } from '@/lib/api';
import { Button, Header, Input, Loading, Screen, useConfirm } from '@/components/ui';
import { spacing } from '@/theme';

export default function EditProperty() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const propertyId = id!;
  const qc = useQueryClient();
  const q = useQuery({ queryKey: ['property', propertyId], queryFn: () => propertiesApi.get(propertyId) });

  const confirm = useConfirm();
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

  // Destructive + irreversible, so it's password-gated: confirm → re-auth →
  // delete. A wrong password re-opens the sheet with an inline error.
  const remove = async () => {
    let passwordError: string | undefined;
    for (;;) {
      const { confirmed, password } = await confirm({
        title: 'Delete this property?',
        message: `This permanently removes “${name || 'this property'}” and all its documented assets. This cannot be undone.`,
        confirmLabel: 'Delete property',
        destructive: true,
        icon: 'trash-outline',
        requirePassword: true,
        passwordError,
      });
      if (!confirmed) return;

      try {
        const ok = await usersApi.verifyPassword(password ?? '');
        if (!ok) {
          passwordError = 'Incorrect password. Please try again.';
          continue;
        }
      } catch {
        Alert.alert('Could not verify', 'Please check your connection and try again.');
        return;
      }

      try {
        await propertiesApi.remove(propertyId);
        qc.invalidateQueries({ queryKey: ['properties'] });
        router.replace('/(app)/(tabs)/properties' as never);
      } catch (e: any) {
        Alert.alert('Could not delete', e?.message ?? 'Try again.');
      }
      return;
    }
  };

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
