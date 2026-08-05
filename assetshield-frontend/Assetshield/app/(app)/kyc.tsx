import { Ionicons } from '@expo/vector-icons';
import { Image } from 'expo-image';
import { router, useLocalSearchParams } from 'expo-router';
import { useState } from 'react';
import { Pressable, View } from 'react-native';
import { isApiError, usersApi } from '@/lib/api';
import { useAuth } from '@/lib/auth/AuthProvider';
import { buildFileForm, CapturedImage, pickImage } from '@/lib/media/capture';
import { Button, Card, Header, Screen, Text, showAlert } from '@/components/ui';
import { colors, spacing } from '@/theme';

/** Ghana Card (KYC) upload — used both from Profile and as an onboarding step. */
export default function Kyc() {
  const { user, refreshUser, clearOnboarding } = useAuth();
  const { onboarding } = useLocalSearchParams<{ onboarding?: string }>();
  const isOnboarding = onboarding === '1';
  const [image, setImage] = useState<CapturedImage | null>(null);
  const [loading, setLoading] = useState(false);

  const finish = () => {
    if (isOnboarding) {
      clearOnboarding();
      router.replace('/(app)/(tabs)/home' as never);
    } else {
      router.back();
    }
  };

  const choose = async (source: 'camera' | 'library') => {
    const img = await pickImage(source).catch(() => null);
    if (img) setImage(img);
  };

  const submit = async () => {
    if (!image) return;
    setLoading(true);
    try {
      await usersApi.uploadGhanaCard(buildFileForm(image));
      await refreshUser();
      showAlert('Submitted', 'Your Ghana Card was uploaded for verification.', [{ text: 'OK', onPress: finish }]);
    } catch (e) {
      if (isApiError(e) && e.code === 'UNSUPPORTED_MEDIA_TYPE') showAlert('Unsupported file', 'Please use a JPEG or PNG image.');
      else showAlert('Upload failed', isApiError(e) ? e.message : 'Try again.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Screen footer={<Button title="Submit for verification" loading={loading} disabled={!image} onPress={submit} />}>
      {isOnboarding ? (
        <View style={{ paddingHorizontal: spacing.screenPadding, paddingTop: spacing.md, flexDirection: 'row', justifyContent: 'flex-end' }}>
          <Pressable hitSlop={12} onPress={finish}>
            <Text variant="labelMd" color={colors.primary}>
              Skip for now
            </Text>
          </Pressable>
        </View>
      ) : (
        <Header title="Ghana Card (KYC)" />
      )}

      {isOnboarding ? (
        <View style={{ gap: spacing.xs }}>
          <Text variant="headlineLgMobile">Verify your identity</Text>
          <Text variant="bodyMd" color={colors.textMuted}>
            One quick step to secure your account. You can also do this later from your profile.
          </Text>
        </View>
      ) : null}

      {user?.ghanaCardUploaded ? (
        <Card style={{ backgroundColor: colors.success }}>
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: spacing.md }}>
            <Ionicons name="checkmark-circle" size={24} color={colors.white} />
            <Text variant="bodyMd" color={colors.white} style={{ flex: 1 }}>
              Your Ghana Card is on file.
            </Text>
          </View>
        </Card>
      ) : null}

      <Text variant="bodyMd" color={colors.textMuted}>
        Upload a clear photo of the front of your Ghana Card. This helps verify ownership of your assets.
      </Text>

      {image ? (
        <Card padded={false} style={{ overflow: 'hidden' }}>
          <Image source={{ uri: image.uri }} style={{ width: '100%', height: 200 }} contentFit="cover" />
        </Card>
      ) : null}

      <View style={{ flexDirection: 'row', gap: spacing.md }}>
        <Button title="Take photo" variant="secondary" onPress={() => choose('camera')} />
        <Button title="From gallery" variant="secondary" onPress={() => choose('library')} />
      </View>
    </Screen>
  );
}
