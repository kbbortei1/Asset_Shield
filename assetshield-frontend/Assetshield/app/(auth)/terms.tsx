import { LegalContent } from '@/components/legal/LegalContent';
import { Header, Screen, Text } from '@/components/ui';
import { colors, spacing } from '@/theme';

/** Terms & privacy shown from the sign-up consent link (reachable while signed out). */
export default function Terms() {
  return (
    <Screen>
      <Header title="Terms & privacy" />
      <Text variant="bodyMd" color={colors.textMuted} style={{ marginBottom: spacing.sm }}>
        By creating an account you agree to use AssetShield GH lawfully and honestly — the
        evidence you document is only as trustworthy as the care you take capturing it. Below is
        how we handle and protect your data.
      </Text>
      <LegalContent />
    </Screen>
  );
}
