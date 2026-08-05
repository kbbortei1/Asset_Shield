import { Ionicons } from '@expo/vector-icons';
import { router } from 'expo-router';
import { useState } from 'react';
import { LayoutAnimation, Platform, UIManager, View } from 'react-native';
import { useAuth } from '@/lib/auth/AuthProvider';
import { Button, Card, Header, Screen, Text } from '@/components/ui';
import { colors, radius, spacing } from '@/theme';

if (Platform.OS === 'android' && UIManager.setLayoutAnimationEnabledExperimental) {
  UIManager.setLayoutAnimationEnabledExperimental(true);
}

type Faq = { q: string; a: string };
type Section = { title: string; icon: keyof typeof Ionicons.glyphMap; items: Faq[] };

const OWNER: Section[] = [
  {
    title: 'Getting started',
    icon: 'rocket-outline',
    items: [
      { q: 'What is AssetShield?', a: 'AssetShield lets you photograph and permanently "fingerprint" your belongings, so if disaster strikes you have trusted, tamper-proof evidence for an insurance claim.' },
      { q: 'How do I add a property?', a: 'Tap the "+" on Home → New property, then give it a name and location. A property is a place — like your home or shop — that holds your assets.' },
    ],
  },
  {
    title: 'Documenting your assets',
    icon: 'camera-outline',
    items: [
      { q: 'How do I document an asset?', a: 'Open a property and tap "Capture asset". Take photos of the item (e.g. "Kitchen") — up to 15 per asset — add a description, category and, optionally, a value, then Save.' },
      { q: 'Why can\'t I upload from my gallery?', a: 'Evidence must be captured live in the app. Gallery uploads could be edited or AI-generated, which would break the tamper-evidence guarantee insurers rely on.' },
      { q: 'What does the "VERIFIED" fingerprint mean?', a: 'When you take a photo we compute a SHA-256 hash of the exact image, plus its GPS and timestamp. That fingerprint proves the photo has not been altered.' },
      { q: 'Why does it ask for my location?', a: 'The location tags where a photo was taken and helps automatically match your assets to damage later. It is optional but recommended.' },
      { q: 'I saved an asset without a value — where is it?', a: 'It still appears in your asset list; a value is optional. You can add or edit it anytime by opening the asset.' },
    ],
  },
  {
    title: 'Damage & dossiers',
    icon: 'document-text-outline',
    items: [
      { q: 'How do I report damage?', a: 'Open the affected asset and tap "Report damage" (or start one from the property). Pick what happened — fire, flood, theft, storm — then photograph the damage. It links to that asset for clear before-and-after evidence.' },
      { q: 'What is a dossier?', a: 'A dossier is a tamper-evident PDF that bundles your evidence (assets + damage) into a signed pack an insurer can trust has not been altered.' },
      { q: 'How do I generate a dossier?', a: 'Complete a damage report, then tap "Pay & generate dossier". After paying, tap "Confirm payment" and the dossier is generated.' },
      { q: 'How do I view my dossier?', a: 'Open it from the Dossiers tab. You can preview the evidence in-app and tap "View dossier" to read the full PDF.' },
      { q: 'I deleted my property — where is my dossier?', a: 'Deleting a property removes it and its assets, but any dossiers you already generated stay available in the Dossiers tab.' },
    ],
  },
  {
    title: 'Working with insurers',
    icon: 'people-outline',
    items: [
      { q: 'How do I share a dossier with an insurer?', a: 'Open the dossier and tap "Share with an agent". Only agents you connect with can see it.' },
      { q: 'How do I chat with an insurer?', a: 'Tap the chat icon next to the notification bell to open Messages and see everyone you\'re connected with, or open a connection to chat.' },
      { q: 'How do agents find my property?', a: 'Only if you turn on "Open to insurance offers" on a property. Agents then see a limited lead — no phone number or exact value — and can request to connect.' },
    ],
  },
  {
    title: 'Payments, plans & account',
    icon: 'card-outline',
    items: [
      { q: 'How do payments work?', a: 'Payments run through Paystack (mobile money or card). After paying in the browser, return to the app and tap "Confirm payment" to finish.' },
      { q: 'What\'s the difference between Free and PRO?', a: 'Free lets you document one property with up to 30 photos. PRO unlocks unlimited properties, priority dossier generation and marketplace offers from agents.' },
      { q: 'How do I add household members?', a: 'Open a property → Household → invite a phone number. Members can help document; you control who can export.' },
    ],
  },
];

const AGENT: Section[] = [
  {
    title: 'Getting started',
    icon: 'rocket-outline',
    items: [
      { q: 'What can I do as an agent?', a: 'Discover owner leads, receive tamper-evident dossiers, verify they\'re genuine, chat with owners, and send quotes.' },
      { q: 'How do I get verified?', a: 'Your account is reviewed after registration (NIC licence + insurer). You\'ll be notified once you\'re verified.' },
    ],
  },
  {
    title: 'Leads & connections',
    icon: 'pricetags-outline',
    items: [
      { q: 'How do I find leads?', a: 'The Leads tab shows owners open to offers in your area (subscription required). Express interest to connect.' },
      { q: 'Why do leads show limited info?', a: 'To protect owners, a lead shows only their name, property name, type and locality — no phone number or exact values — until the owner accepts you.' },
    ],
  },
  {
    title: 'Dossiers & verification',
    icon: 'shield-checkmark-outline',
    items: [
      { q: 'How do I receive a dossier?', a: 'When an owner shares one with you it appears under Dossiers (Shared).' },
      { q: 'How do I know a dossier is genuine?', a: 'Open it — we recompute the manifest hash and show a "Tamper-evident: verified" badge if nothing was altered. Any mismatch is flagged so you can treat it with caution.' },
      { q: 'How do I read the full dossier?', a: 'Open the shared dossier and tap "View full dossier" to read the PDF in the app.' },
    ],
  },
  {
    title: 'Chat & quotes',
    icon: 'chatbubbles-outline',
    items: [
      { q: 'How do I chat with an owner?', a: 'From a shared dossier tap "Chat with owner", or use the chat icon by the notification bell to see all your conversations.' },
      { q: 'How do I send a quote?', a: 'Open a shared dossier and tap "Send a quote" — enter the coverage amount, monthly premium and term.' },
    ],
  },
  {
    title: 'Subscription & payments',
    icon: 'card-outline',
    items: [
      { q: 'What does my subscription unlock?', a: 'Owner leads, shared dossiers and the ability to send quotes.' },
      { q: 'How does payment work?', a: 'Via Paystack (mobile money or card). After paying in the browser, return to the app and tap "Confirm payment".' },
    ],
  },
];

/** In-app help centre: role-aware FAQs with an expandable accordion. */
export default function Help() {
  const { user } = useAuth();
  const isAgent = user?.role === 'AGENT';
  const sections = isAgent ? AGENT : OWNER;
  const [open, setOpen] = useState<string | null>(null);

  const toggle = (key: string) => {
    LayoutAnimation.configureNext(LayoutAnimation.Presets.easeInEaseOut);
    setOpen((cur) => (cur === key ? null : key));
  };

  return (
    <Screen>
      <Header title="Help centre" />
      <Text variant="bodyMd" color={colors.textMuted}>
        Answers to the questions {isAgent ? 'agents' : 'owners'} ask most. Tap a question to see the answer.
      </Text>

      {sections.map((section) => (
        <View key={section.title} style={{ gap: spacing.sm }}>
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: spacing.sm, marginTop: spacing.sm }}>
            <Ionicons name={section.icon} size={18} color={colors.primary} />
            <Text variant="labelMd" weight="semibold" color={colors.primary}>
              {section.title}
            </Text>
          </View>
          {section.items.map((item, i) => {
            const key = `${section.title}-${i}`;
            const isOpen = open === key;
            return (
              <Card key={key} padded onPress={() => toggle(key)}>
                <View style={{ flexDirection: 'row', alignItems: 'center', gap: spacing.md }}>
                  <Text variant="bodyMd" weight="semibold" style={{ flex: 1 }}>
                    {item.q}
                  </Text>
                  <Ionicons name={isOpen ? 'chevron-up' : 'chevron-down'} size={18} color={colors.textMuted} />
                </View>
                {isOpen ? (
                  <Text variant="bodyMd" color={colors.textMuted} style={{ marginTop: spacing.sm, lineHeight: 22 }}>
                    {item.a}
                  </Text>
                ) : null}
              </Card>
            );
          })}
        </View>
      ))}

      <Card style={{ backgroundColor: colors.tealTint, marginTop: spacing.md }}>
        <View style={{ gap: spacing.md }}>
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: spacing.sm }}>
            <Ionicons name="help-buoy-outline" size={20} color={colors.primary} />
            <Text variant="bodyMd" weight="semibold" color={colors.primary} style={{ flex: 1 }}>
              Still need help?
            </Text>
          </View>
          <Text variant="labelMd" color={colors.primary}>
            Can't find your answer here? Send us the details and we'll get back to you.
          </Text>
          <Button
            title="Report a problem"
            variant="secondary"
            onPress={() => router.push('/(app)/report-problem' as never)}
          />
        </View>
      </Card>

      <View style={{ height: spacing.xl, borderRadius: radius.md }} />
    </Screen>
  );
}
