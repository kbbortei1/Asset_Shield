import { api } from '../client';
import {
  AgentInterest,
  AgentProfile,
  CreateQuoteRequest,
  DossierVerification,
  InterestMessage,
  Lead,
  Payment,
  PaymentHandle,
  ProSubscription,
  Quote,
  SharedDossier,
} from '../models';
import { Page, PageParams } from '../types';

export const marketplaceApi = {
  // owner PRO — subscription-init returns a FLAT payment handle (no `payment` wrapper)
  buyPro: () => api.post<PaymentHandle>('/subscriptions/pro'),
  mySubscription: () => api.get<ProSubscription>('/users/me/subscription'),

  // admin agent verification
  adminAgents: (status = 'PENDING_VERIFICATION', params?: PageParams) =>
    api.get<Page<AgentProfile>>('/admin/agents', { query: { status, ...params } }),
  verifyAgent: (agentRecordId: string, approve: boolean, rejectionReason?: string | null) =>
    api.put<AgentProfile>(`/admin/agents/${agentRecordId}/verify`, { approve, rejectionReason: rejectionReason ?? null }),

  // agent
  agentMe: () => api.get<AgentProfile>('/agents/me'),
  agentSubscription: () => api.get<ProSubscription>('/agents/me/subscription'),
  subscribeAgent: () => api.post<PaymentHandle>('/agents/me/subscription'),
  leads: (params?: PageParams) => api.get<Page<Lead>>('/agents/me/leads', { query: params }),
  agentInterests: (params?: PageParams) => api.get<Page<AgentInterest>>('/agents/me/interests', { query: params }),
  sharedDossiers: (params?: PageParams) => api.get<Page<SharedDossier>>('/agents/me/shared-dossiers', { query: params }),
  expressInterest: (propertyId: string) => api.post<AgentInterest>(`/leads/${propertyId}/express-interest`),

  // owner consent
  myAgentInterests: (params?: PageParams) =>
    api.get<Page<AgentInterest>>('/users/me/agent-interests', { query: params }),
  respondInterest: (interestId: string, accept: boolean) =>
    api.put<AgentInterest>(`/agent-interests/${interestId}/respond`, { accept }),
  revokeInterest: (interestId: string) => api.del<void>(`/agent-interests/${interestId}`),

  // owner<->agent chat (scoped to an accepted interest)
  messages: (interestId: string, params?: PageParams) =>
    api.get<Page<InterestMessage>>(`/agent-interests/${interestId}/messages`, { query: params }),
  sendMessage: (interestId: string, body: string) =>
    api.post<InterestMessage>(`/agent-interests/${interestId}/messages`, { body }),
  shareToAgent: (dossierId: string, agentInterestId: string) =>
    api.post<SharedDossier>(`/dossiers/${dossierId}/share-to-agent`, { agentInterestId }),
  revokeShare: (dossierId: string, agentRecordId: string) =>
    api.del<void>(`/dossiers/${dossierId}/share-to-agent/${agentRecordId}`),

  // quotes
  verifyDossier: (dossierId: string) => api.get<DossierVerification>(`/dossiers/${dossierId}/verify`),
  agentDownloadDossier: (dossierId: string) =>
    api.get<{ downloadUrl: string; fileName: string }>(`/dossiers/${dossierId}/agent-download`),
  sendQuote: (dossierId: string, body: CreateQuoteRequest) => api.post<Quote>(`/dossiers/${dossierId}/quote`, body),
  myQuotes: (params?: PageParams) => api.get<Page<Quote>>('/users/me/quotes', { query: params }),
  respondQuote: (quoteId: string, accept: boolean) => api.put<Quote>(`/quotes/${quoteId}/respond`, { accept }),

  // payments
  verifyPayment: (reference: string) => api.post<Payment>(`/payments/${reference}/verify`),
  getPayment: (reference: string) => api.get<Payment>(`/payments/${reference}`),
  /** The caller's billing history, newest first (payment-service). */
  myPayments: (params?: PageParams) => api.get<Page<Payment>>('/users/me/payments', { query: params }),
};
