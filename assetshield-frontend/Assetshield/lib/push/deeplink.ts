import { NotificationType } from '@/lib/api';

/**
 * Map a notification type + payload to an in-app route (handoff §7). Payload
 * keys are strings and may vary, so we read defensively and fall back to the
 * inbox for anything unknown.
 */
export function routeForNotification(type: string | undefined, payload?: Record<string, string>): string {
  const p = payload ?? {};
  switch (type as NotificationType) {
    case 'TIP':
      return p.propertyId ? `/(app)/property/${p.propertyId}/tips` : '/(app)/tips';
    case 'REDOC_REMINDER':
      return p.propertyId ? `/(app)/property/${p.propertyId}` : '/(app)/(tabs)/properties';
    case 'DOSSIER_READY':
      return p.dossierId ? `/(app)/dossier/${p.dossierId}` : '/(app)/(tabs)/activity';
    case 'HOUSEHOLD_INVITE':
      return '/(app)/invitations';
    case 'AGENT_INTEREST':
      return '/(app)/(tabs)/market';
    case 'INTEREST_RESPONSE':
    case 'INTEREST_REVOKED':
      return '/(app)/(tabs)/market';
    case 'SHARE_CREATED':
      return p.dossierId ? `/(app)/agent/dossier/${p.dossierId}` : '/(app)/(tabs)/activity';
    case 'SHARE_REVOKED':
      return '/(app)/(tabs)/activity';
    case 'QUOTE_ISSUED':
    case 'QUOTE_RESPONSE':
      return '/(app)/(tabs)/activity';
    case 'AGENT_VERIFIED':
    case 'AGENT_REJECTED':
      return '/(app)/(tabs)/home';
    case 'SUBSCRIPTION_EXPIRY':
      return '/(app)/subscription';
    default:
      return '/(app)/(tabs)/notifications';
  }
}
