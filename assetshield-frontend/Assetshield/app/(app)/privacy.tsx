import { LegalContent } from '@/components/legal/LegalContent';
import { Header, Screen } from '@/components/ui';

/** Privacy & data — how AssetShield stores and protects your information. */
export default function Privacy() {
  return (
    <Screen>
      <Header title="Privacy & data" />
      <LegalContent />
    </Screen>
  );
}
