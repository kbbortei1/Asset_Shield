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
export async function runCheckout(payment: PaymentHandle): Promise<'success' | 'failed' | 'pending'> {
  const url = payment.authorizationUrl;
  if (url && /^https?:\/\//i.test(url)) {
    try {
      await WebBrowser.openBrowserAsync(url); // resolves when the user returns
    } catch {
      // ignore — proceed to verify regardless (mock/stub)
    }
  }
  try {
    const res = await marketplaceApi.verifyPayment(payment.reference);
    if (res.status === 'SUCCESS') return 'success';
    if (res.status === 'FAILED') return 'failed';
    return 'pending'; // still INITIATED — checkout abandoned or MoMo confirming
  } catch {
    return 'pending';
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
