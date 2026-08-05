import { Redirect } from 'expo-router';

/**
 * Safety net for the `assetshield://payment-callback` deep link. We no longer
 * rely on it to return from checkout (the payment screen verifies on return and
 * has a "Confirm payment" button), but if any old/cached Paystack redirect ever
 * fires it, this bounces cleanly back into the app instead of showing
 * "Unmatched Route". The (app) layout handles auth if the app was cold-started.
 */
export default function PaymentCallback() {
  return <Redirect href="/(app)/(tabs)/home" />;
}
