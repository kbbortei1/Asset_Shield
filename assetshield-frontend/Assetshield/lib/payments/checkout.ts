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
const VERIFY_INTERVAL_MS = 1500;

export async function runCheckout(payment: PaymentHandle): Promise<'success' | 'failed' | 'pending'> {
  const url = payment.authorizationUrl;
  if (url && /^https?:\/\//i.test(url)) {
    try {
      await WebBrowser.openBrowserAsync(url); // resolves when the user returns
    } catch {
      // ignore — proceed to verify regardless (mock/stub)
    }
  }
  // A single immediate verify races the settlement: mock mode only settles
  // after MOCK_AUTO_SETTLE_MS, and Paystack needs a moment after the browser
  // closes. Both made a genuinely paid dossier report as "not completed".
  const deadline = Date.now() + VERIFY_TIMEOUT_MS;
  for (;;) {
    try {
      const res = await marketplaceApi.verifyPayment(payment.reference);
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
