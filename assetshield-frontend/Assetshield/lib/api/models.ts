/**
 * Domain DTOs — field names verified against the backend Java response records
 * (auth/property/damage/marketplace/notification services). Where a field is
 * marked optional it may be absent on some endpoints (list vs detail).
 */

// ---- enums (serialized as .name() strings) ----
export type UserRole = 'OWNER' | 'AGENT' | 'ADMIN';
export type AccessLevel = 'OWNER' | 'MEMBER_EXPORT' | 'MEMBER' | 'NONE';
export type PropertyType = 'RESIDENTIAL' | 'COMMERCIAL' | 'RENTAL';
export type AssetCategory = 'ELECTRONICS' | 'FURNITURE' | 'CLOTHING_STOCK' | 'MACHINERY' | 'DOCUMENTS' | 'OTHER';
export type DisasterType = 'FIRE' | 'FLOOD' | 'THEFT' | 'STORM' | 'OTHER';
export type DamageReportStatus = 'DRAFT' | 'COMPLETED';
export type DossierStatus = 'PENDING_PAYMENT' | 'GENERATING' | 'READY' | 'FAILED';
export type PairingMethod = 'GPS_AUTO' | 'MANUAL';
export type TipsFrequency = 'DAILY' | 'WEEKLY' | 'OFF';
export type Platform = 'ANDROID' | 'IOS';
export type AgentStatus = 'PENDING_VERIFICATION' | 'VERIFIED' | 'REJECTED';
export type InterestStatus = 'PENDING' | 'ACCEPTED' | 'DECLINED' | 'REVOKED';
export type QuoteStatus = 'PENDING' | 'ACCEPTED' | 'DECLINED';
export type SubscriptionStatus = 'ACTIVE' | 'EXPIRED' | 'CANCELLED' | 'NONE';
export type PaymentStatus = 'INITIATED' | 'SUCCESS' | 'FAILED';

// ---- auth & user ----
export type TokenPair = { accessToken: string; refreshToken: string };
export type AuthResult = TokenPair & { expiresInSeconds?: number; user?: UserSummary };
export type UserSummary = { id: string; fullName: string; role: UserRole; language?: string };

export type RegisterRequest = { phoneNumber: string; password: string; fullName: string };
export type RegisterAgentRequest = RegisterRequest & { insurerName: string; nicLicenceNo: string };
export type VerifyOtpRequest = { phoneNumber: string; code: string };
export type LoginRequest = { phoneNumber: string; password: string };

export type UserProfile = {
  id: string;
  phoneNumber: string;
  fullName: string;
  role: UserRole;
  language?: string;
  ghanaCardUploaded?: boolean;
  avatarUrl?: string | null; // signed, ~15min TTL (relative in local storage)
  createdAt?: string;
};

// ---- properties / assets / household ----
export type PropertyDashboard = {
  assetCount: number;
  totalEstimatedValue: number;
  byCategory?: { category: AssetCategory | string; count: number; value: number }[];
};

export type Property = {
  id: string;
  name: string;
  type: PropertyType;
  gpsLat?: number;
  gpsLng?: number;
  locality?: string;
  openToOffers?: boolean;
  openToOffersAt?: string;
  lastDocumentedAt?: string;
  myAccess?: AccessLevel;
  createdAt?: string;
  // list item only:
  assetCount?: number;
  totalEstimatedValue?: number;
  // detail only:
  dashboard?: PropertyDashboard;
};

export type CreatePropertyRequest = {
  name: string;
  type: PropertyType;
  gpsLat?: number;
  gpsLng?: number;
  locality?: string;
};

export type AssetReceipt = { id: string; receiptUrl: string; createdAt?: string };

export type Asset = {
  id: string;
  propertyId: string;
  description: string;
  category?: AssetCategory;
  estimatedValue?: number;
  photoUrl?: string; // signed, ~15min TTL
  sha256Hash: string;
  gpsLat?: number;
  gpsLng?: number;
  capturedAt?: string;
  warrantyExpiresOn?: string | null; // YYYY-MM-DD
  nextServiceOn?: string | null; // YYYY-MM-DD
  receiptCount?: number; // list/flat response
  receipts?: AssetReceipt[]; // detail response
  createdByUserId?: string;
  createdAt?: string;
  /** Create response only: same photo already documents another property. */
  duplicateWarning?: boolean | null;
};

/** Metadata JSON part sent alongside the `file` part for an asset upload. */
export type AssetMetadata = {
  sha256Hash: string;
  gpsLat?: number;
  gpsLng?: number;
  capturedAt: string;
  description: string;
  estimatedValue?: number;
  category?: AssetCategory;
  warrantyExpiresOn?: string; // YYYY-MM-DD
  nextServiceOn?: string; // YYYY-MM-DD
};

/** ?q= / ?category= / value-range filters for the asset list (smart search). */
export type AssetSearchFilters = {
  q?: string;
  category?: AssetCategory;
  minValue?: number;
  maxValue?: number;
};

// ---- insights ----
export type TimelineEventType = 'PROPERTY_CREATED' | 'ASSET_ADDED' | 'ASSET_REMOVED' | 'RECEIPT_ADDED';

export type TimelineEvent = {
  type: TimelineEventType;
  at: string;
  assetId?: string | null;
  label: string;
};

export type AnalyticsCategoryLine = { category: AssetCategory; count: number; value: number };
export type AnalyticsPropertyLine = {
  propertyId: string;
  name: string;
  assetCount: number;
  totalValue: number;
};

export type AssetAnalytics = {
  propertyCount: number;
  assetCount: number;
  totalValue: number;
  byCategory: AnalyticsCategoryLine[];
  byProperty: AnalyticsPropertyLine[];
};

/** Admin-only security audit row; actorUserId is null for unknown-phone failures. */
export type AuditEvent = {
  id: string;
  actorUserId?: string | null;
  action: string;
  target?: string | null;
  detail?: string | null;
  createdAt: string;
};

export type Member = {
  membershipId: string;
  userId: string;
  fullName: string;
  phoneNumber?: string;
  canExport?: boolean;
  createdAt?: string;
};

export type InviteRequest = { inviteePhone: string; canExport: boolean };

export type Invitation = {
  id: string;
  propertyName?: string;
  ownerName?: string;
  canExport?: boolean;
  expiresAt?: string;
};

// ---- damage / dossiers ----
export type DamageReport = {
  id: string;
  propertyId?: string;
  propertyName?: string;
  disasterType: DisasterType;
  description?: string;
  occurredAt?: string;
  status: DamageReportStatus;
  photoCount?: number;
  totalEstimatedLoss?: number;
  completedAt?: string;
  createdAt?: string;
  photos?: DamagePhoto[];
  pairs?: Pair[];
};

export type CreateDamageReportRequest = {
  disasterType: DisasterType;
  description?: string;
  occurredAt?: string;
};

export type DamagePhoto = {
  id: string;
  photoUrl?: string;
  sha256Hash: string;
  gpsLat?: number;
  gpsLng?: number;
  capturedAt?: string;
  description?: string;
  paired?: boolean;
};

/** Metadata JSON part sent alongside the `file` part for a damage-photo upload. */
export type DamagePhotoMetadata = {
  sha256Hash: string;
  gpsLat?: number;
  gpsLng?: number;
  capturedAt: string;
  description?: string;
};

/** GPS-matched documented asset suggested for pairing (returned with an upload). */
export type PairingSuggestion = {
  assetId: string;
  distanceMeters?: number;
  description?: string;
  estimatedValue?: number;
  category?: AssetCategory;
  thumbnailUrl?: string;
  capturedAt?: string;
};

/** Damage photo upload response is NESTED (data.photo + data.pairingSuggestions). */
export type DamagePhotoUploadResult = {
  photo: DamagePhoto;
  pairingSuggestions: PairingSuggestion[];
};

export type Pair = {
  id: string;
  damagePhotoId: string;
  assetId: string;
  pairingMethod: PairingMethod;
  distanceMeters?: number;
};
export type CreatePairRequest = { damagePhotoId: string; assetId: string; pairingMethod: PairingMethod };

export type PaymentHandle = {
  reference: string;
  authorizationUrl: string;
  paymentId?: string;
  amount?: number;
  currency?: string;
};

export type GenerateDossierResult = {
  dossierId: string;
  status: DossierStatus;
  payment: PaymentHandle;
};

export type Dossier = {
  id: string;
  damageReportId?: string;
  propertyName?: string;
  disasterType?: DisasterType;
  status: DossierStatus;
  totalEstimatedLoss?: number;
  generatedAt?: string;
};

export type DossierStatusResult = {
  dossierId?: string;
  status: DossierStatus;
  totalEstimatedLoss?: number;
  pageCount?: number;
  manifestHash?: string;
  generatedAt?: string;
  failureReason?: string;
};
export type DossierDownload = { downloadUrl: string; fileName: string };

// ---- marketplace / consent / payments ----
export type SubscriptionLimits = { maxProperties?: number; maxPhotosPerProperty?: number } | null;

export type ProSubscription = {
  status?: SubscriptionStatus;
  tier?: 'PRO' | 'FREE';
  plan?: string;
  startedAt?: string;
  expiresAt?: string;
  limits?: SubscriptionLimits;
};

export type AgentProfile = {
  agentId?: string;
  id?: string; // admin-list variant may use a different id
  fullName?: string;
  insurerName?: string;
  nicLicenceNo?: string;
  verificationStatus?: AgentStatus;
  status?: AgentStatus; // admin-list variant
  rejectionReason?: string | null;
  subscription?: { status?: SubscriptionStatus; expiresAt?: string } | null;
};

/** Leads are exactly 5 fields by design (handoff §10). No GPS/phone/value. */
export type Lead = {
  propertyId: string;
  ownerDisplayName: string;
  propertyName: string;
  propertyType: PropertyType;
  locality: string;
};

export type AgentInterest = {
  interestId: string;
  propertyName?: string;
  locality?: string;
  agentName?: string;
  insurerName?: string;
  ownerFullName?: string;
  nicLicenceNo?: string;
  status: InterestStatus;
  createdAt?: string;
  respondedAt?: string;
};

export type SharedDossier = {
  dossierId: string;
  shareId?: string;
  agentInterestId?: string;
  ownerName?: string;
  propertyName?: string;
  disasterType?: DisasterType;
  totalEstimatedLoss?: number;
  sharedAt?: string;
};

export type Quote = {
  quoteId: string;
  agentName?: string;
  insurerName?: string;
  propertyName?: string;
  coverageAmount: number;
  premium: number;
  termMonths: number;
  status: QuoteStatus;
  createdAt?: string;
};
export type CreateQuoteRequest = { coverageAmount: number; premium: number; termMonths: number };

export type DossierVerification = {
  dossierId?: string;
  manifestHash?: string;
  recomputedHash?: string;
  tamperEvident?: boolean;
  photoCount?: number;
  mismatches?: { objectPath: string; expected: string; actual: string }[];
  verifiedAt?: string;
};

export type Payment = {
  reference: string;
  purpose?: string; // PRO_SUBSCRIPTION | DOSSIER_FEE | AGENT_SUBSCRIPTION
  amount?: number;
  currency?: string;
  status: PaymentStatus;
  createdAt?: string;
};

// ---- notifications / tips ----
export type NotificationType =
  | 'TIP'
  | 'REDOC_REMINDER'
  | 'DOSSIER_READY'
  | 'HOUSEHOLD_INVITE'
  | 'AGENT_INTEREST'
  | 'INTEREST_RESPONSE'
  | 'INTEREST_REVOKED'
  | 'SHARE_CREATED'
  | 'SHARE_REVOKED'
  | 'QUOTE_ISSUED'
  | 'QUOTE_RESPONSE'
  | 'AGENT_VERIFIED'
  | 'AGENT_REJECTED'
  | 'SUBSCRIPTION_EXPIRY'
  | 'MAINTENANCE_DUE'
  | 'ANNOUNCEMENT'
  | 'CHAT_MESSAGE';

export type InterestMessage = {
  id: string;
  senderUserId: string;
  senderRole: 'OWNER' | 'AGENT';
  body: string;
  createdAt: string;
};

// ---- support / problem reports ----
export type ReportCategory = 'BUG' | 'PAYMENT' | 'ACCOUNT' | 'SUGGESTION' | 'OTHER';

export type CreateReportRequest = { category: ReportCategory; message: string; context?: string };

export type ProblemReport = {
  id: string;
  category: ReportCategory;
  message: string;
  context?: string | null;
  status: 'OPEN' | 'RESOLVED';
  reporterUserId?: string;
  reporterName?: string | null;
  reporterPhone?: string | null;
  createdAt: string;
  resolvedAt?: string | null;
};

// ---- admin broadcast ----
export type BroadcastAudience = 'EVERYONE' | 'OWNERS' | 'AGENTS' | 'SPECIFIC';
export type AudienceCounts = { everyone: number; owners: number; agents: number };
export type AdminUserItem = { id: string; fullName: string; phoneNumber: string; role: UserRole };
export type BroadcastRequest = {
  audience: BroadcastAudience;
  userIds?: string[];
  title: string;
  body: string;
};

export type AppNotification = {
  id: string;
  type: NotificationType | string;
  title?: string;
  body?: string;
  payload?: Record<string, string>;
  status?: string;
  sentAt?: string;
  createdAt: string;
};

export type Tip = {
  id: string;
  propertyId?: string;
  tipText: string;
  category?: string;
  createdAt?: string;
  readAt?: string | null;
};

export type DeviceTokenRequest = { fcmToken: string; platform: Platform };
export type NotificationPreferences = {
  tipsFrequency: TipsFrequency;
  pushEnabled: boolean;
  inAppEnabled: boolean;
};
