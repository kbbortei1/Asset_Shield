import * as WebBrowser from 'expo-web-browser';
import { damageApi, DossierStatus, marketplaceApi, PaymentHandle } from '@/lib/api';

/**
 * Drive the Paystack checkout (handoff §6). In mock mode the authorizationUrl is
 * a stub and payment auto-settles ~2s after generate — we still call verify and
 * then the caller polls status, so the same code path works in both modes.
 *
 * Outcomes: 'success' = settled; 'failed' = provider declined; 'pending' = not
 * completed yet (user closed the checkout without paying, or verification could
 * not confirm) — the caller should tell the user they can retry any time.
 */
/** Settlement is asynchronous, so verify is polled rather than asked once. */
const VERIFY_TIMEOUT_MS = 25_000;
const VERIFY_INTERVAL_MS = 1200;

export type PaymentOutcome = 'success' | 'failed' | 'pending';

export async function runCheckout(payment: PaymentHandle): Promise<PaymentOutcome> {
  const url = payment.authorizationUrl;
  if (url && /^https?:\/\//i.test(url)) {
    try {
      // Plain in-app browser. It resolves when the user closes it (tap X/back)
      // after paying; then we verify. We deliberately do NOT rely on a
      // custom-scheme redirect to auto-close — that was unreliable on Android
      // and could dump the user on an "Unmatched Route". The caller also shows
      // a "Confirm payment" button as the dependable fallback.
      await WebBrowser.openBrowserAsync(url);
    } catch {
      // ignore — proceed to verify regardless (mock/stub or manual close)
    }
  }
  return verifyPaymentUntilSettled(payment.reference);
}

/**
 * Poll verify until the payment settles (SUCCESS/FAILED) or the window elapses.
 * Reusable by a "Confirm payment" button so the user can complete the flow even
 * if the browser round-trip didn't confirm in time (e.g. MoMo still settling).
 */
export async function verifyPaymentUntilSettled(
  reference: string,
  timeoutMs: number = VERIFY_TIMEOUT_MS,
): Promise<PaymentOutcome> {
  if (!reference) return 'pending';
  const deadline = Date.now() + timeoutMs;
  for (;;) {
    try {
      const res = await marketplaceApi.verifyPayment(reference);
      if (res.status === 'SUCCESS') return 'success';
      if (res.status === 'FAILED') return 'failed';
    } catch {
      // transient (network blip / provider not ready) — keep polling
    }
    if (Date.now() >= deadline) return 'pending'; // abandoned, or MoMo still confirming
    await delay(VERIFY_INTERVAL_MS);
  }
}

/**
 * Poll a dossier's status until it leaves the transient states. Resolves with
 * the terminal status (READY/FAILED) or the last seen status on timeout.
 */
export async function pollDossierStatus(
  dossierId: string,
  opts: { intervalMs?: number; timeoutMs?: number; onTick?: (s: DossierStatus) => void } = {},
): Promise<DossierStatus> {
  const interval = opts.intervalMs ?? 2000;
  const deadline = Date.now() + (opts.timeoutMs ?? 60_000);
  let last: DossierStatus = 'PENDING_PAYMENT';
  while (Date.now() < deadline) {
    try {
      const { status } = await damageApi.dossierStatus(dossierId);
      last = status;
      opts.onTick?.(status);
      if (status === 'READY' || status === 'FAILED') return status;
    } catch {
      // transient — keep polling
    }
    await delay(interval);
  }
  return last;
}

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}
