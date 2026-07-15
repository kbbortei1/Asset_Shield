import { Ionicons } from '@expo/vector-icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useLocalSearchParams } from 'expo-router';
import { useState } from 'react';
import { Alert, Switch, View } from 'react-native';
import { isApiError, propertiesApi } from '@/lib/api';
import { Button, Card, EmptyState, Header, Input, Loading, Screen, Text } from '@/components/ui';
import { colors, spacing } from '@/theme';

/** Household: invite a member + manage existing members. */
export default function HouseholdInvite() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const propertyId = id!;
  const qc = useQueryClient();

  const [phone, setPhone] = useState('+233');
  const [canExport, setCanExport] = useState(true);

  const members = useQuery({ queryKey: ['members', propertyId], queryFn: () => propertiesApi.members(propertyId) });

  const invite = useMutation({
    mutationFn: () => propertiesApi.invite(propertyId, { inviteePhone: phone, canExport }),
    onSuccess: () => {
      setPhone('+233');
      Alert.alert('Invitation sent', 'They’ll be notified to join the household.');
    },
    onError: (e) => {
      if (isApiError(e)) {
        if (e.code === 'DUPLICATE_PENDING_INVITE') Alert.alert('Already invited', 'An invite is already pending for this number.');
        else if (e.code === 'ALREADY_MEMBER') Alert.alert('Already a member', 'This person is already in the household.');
        else Alert.alert('Could not invite', e.message);
      }
    },
  });

  const removeMember = useMutation({
    mutationFn: (userId: string) => propertiesApi.removeMember(propertyId, userId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['members', propertyId] }),
  });

  return (
    <Screen>
      <Header title="Household" />
      <Card>
        <View style={{ gap: spacing.lg }}>
          <Text variant="headlineSm">Invite a member</Text>
          <Input label="Phone number" value={phone} onChangeText={setPhone} keyboardType="phone-pad" />
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: spacing.md }}>
            <View style={{ flex: 1 }}>
              <Text variant="bodyMd" weight="semibold">
                Can export dossiers
              </Text>
              <Text variant="labelMd" color={colors.textMuted}>
                Allow this member to generate and download dossiers.
              </Text>
            </View>
            <Switch value={canExport} onValueChange={setCanExport} trackColor={{ true: colors.primary, false: colors.border }} thumbColor={colors.white} />
          </View>
          <Button title="Send invitation" loading={invite.isPending} disabled={phone.length < 8} onPress={() => invite.mutate()} />
        </View>
      </Card>

      <Text variant="headlineSm">Members</Text>
      {members.isLoading ? (
        <Loading />
      ) : (members.data?.length ?? 0) === 0 ? (
        <EmptyState icon="people-outline" title="No members yet" body="Invite family or staff to help document this property." />
      ) : (
        members.data!.map((m) => (
          <Card key={m.userId}>
            <View style={{ flexDirection: 'row', alignItems: 'center', gap: spacing.md }}>
              <Ionicons name="person-circle" size={32} color={colors.primary} />
              <View style={{ flex: 1 }}>
                <Text variant="bodyMd" weight="semibold">
                  {m.fullName}
                </Text>
                <Text variant="labelMd" color={colors.textMuted}>
                  {m.phoneNumber ?? '-'} {m.canExport ? '· can export' : ''}
                </Text>
              </View>
              <Button
                title="Remove"
                variant="ghost"
                fullWidth={false}
                loading={removeMember.isPending}
                onPress={() =>
                  Alert.alert('Remove member?', `Remove ${m.fullName} from this household?`, [
                    { text: 'Cancel', style: 'cancel' },
                    { text: 'Remove', style: 'destructive', onPress: () => removeMember.mutate(m.userId) },
                  ])
                }
              />
            </View>
          </Card>
        ))
      )}
    </Screen>
  );
}
