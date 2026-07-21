import { Ionicons } from '@expo/vector-icons';
import { View } from 'react-native';
import { Card, Text } from '@/components/ui';
import { colors, spacing } from '@/theme';

/**
 * Plain-language privacy & data summary shown on the in-app Privacy screen and
 * during sign-up. Legal references are to Ghanaian law: the Data Protection Act
 * 2012 (Act 843), the Electronic Transactions Act 2008 (Act 772), and the
 * Cybersecurity Act 2020 (Act 1038).
 */
function Bullet({ children }: { children: React.ReactNode }) {
  return (
    <View style={{ flexDirection: 'row', gap: spacing.sm, alignItems: 'flex-start' }}>
      <Ionicons name="checkmark-circle" size={16} color={colors.primary} style={{ marginTop: 2 }} />
      <Text variant="labelMd" color={colors.textMuted} style={{ flex: 1, lineHeight: 20 }}>
        {children}
      </Text>
    </View>
  );
}

function Section({ icon, title, children }: { icon: keyof typeof Ionicons.glyphMap; title: string; children: React.ReactNode }) {
  return (
    <Card>
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: spacing.sm, marginBottom: spacing.md }}>
        <Ionicons name={icon} size={18} color={colors.primary} />
        <Text variant="bodyMd" weight="semibold">
          {title}
        </Text>
      </View>
      <View style={{ gap: spacing.sm }}>{children}</View>
    </Card>
  );
}

export function LegalContent() {
  return (
    <View style={{ gap: spacing.md }}>
      <Text variant="bodyMd" color={colors.textMuted}>
        AssetShield GH is built to protect both your evidence and your privacy. Here’s a
        plain-language summary of how your information is handled. It isn’t legal advice.
      </Text>

      <Section icon="document-text-outline" title="What we collect">
        <Bullet>Your name and phone number, used to create and secure your account.</Bullet>
        <Bullet>Your Ghana Card, only if you upload it, to verify your identity.</Bullet>
        <Bullet>Photos and details of the assets and damage you document, with the GPS location and time you confirm.</Bullet>
        <Bullet>A profile photo, only if you choose to add one.</Bullet>
      </Section>

      <Section icon="lock-closed-outline" title="How your images & records are stored">
        <Bullet>Every photo is fingerprinted with a SHA-256 hash the moment it’s captured, proving it hasn’t been altered.</Bullet>
        <Bullet>Files are kept in secured storage. The app never exposes a public link. Each view uses a fresh, short-lived signed link (about 15 minutes), so your photos are never openly browsable.</Bullet>
        <Bullet>Your records are retained in their original form, so they stay reliable evidence.</Bullet>
      </Section>

      <Section icon="people-outline" title="Who can see your data">
        <Bullet>Only you, and household members you invite, can view your assets.</Bullet>
        <Bullet>An insurance agent sees a damage dossier only after you explicitly share it, and you can revoke that access at any time.</Bullet>
        <Bullet>We never sell your data.</Bullet>
      </Section>

      <Section icon="shield-checkmark-outline" title="Your rights & control">
        <Bullet>View and edit your details anytime.</Bullet>
        <Bullet>Delete your account whenever you want. Your login is disabled immediately, your phone number is released, and your Ghana Card image is erased.</Bullet>
        <Bullet>You choose what to document and what to share.</Bullet>
      </Section>

      <Section icon="library-outline" title="Ghana legal compliance">
        <Bullet>
          <Text variant="labelMd" weight="semibold" color={colors.text}>Data Protection Act, 2012 (Act 843):</Text> we follow the
          eight data-protection principles (s.17), including data security safeguards (s.28): appropriate technical and
          organisational measures, kept up to date, with your right to access and participate.
        </Bullet>
        <Bullet>
          <Text variant="labelMd" weight="semibold" color={colors.text}>Electronic Transactions Act, 2008 (Act 772):</Text> your
          documented records are retained in their original form (s.8) and their integrity preserved (s.7), so they stand as
          admissible electronic evidence.
        </Bullet>
        <Bullet>
          <Text variant="labelMd" weight="semibold" color={colors.text}>Cybersecurity Act, 2020 (Act 1038):</Text> our security
          practices align with Ghana’s cybersecurity framework, read together with Act 843.
        </Bullet>
      </Section>

      <Text variant="labelMd" color={colors.textMuted} style={{ textAlign: 'center' }}>
        Questions about your data? Reach us from Profile → Report a problem.
      </Text>
    </View>
  );
}
