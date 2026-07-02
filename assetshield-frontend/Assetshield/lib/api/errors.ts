/**
 * Error catalogue (handoff §3). We prefer mapping errorCode → our own copy over
 * the server `message`. Codes not listed fall back to a generic message.
 */
export type ErrorCode =
  | 'VALIDATION_FAILED'
  | 'TOKEN_EXPIRED'
  | 'TOKEN_INVALID'
  | 'REFRESH_INVALID'
  | 'REFRESH_EXPIRED'
  | 'REFRESH_REUSED'
  | 'OTP_REQUIRED'
  | 'OTP_INVALID'
  | 'OTP_EXPIRED'
  | 'OTP_THROTTLED'
  | 'BAD_CREDENTIALS'
  | 'PHONE_EXISTS'
  | 'LICENCE_EXISTS'
  | 'RATE_LIMITED'
  | 'FORBIDDEN'
  | 'RESOURCE_NOT_FOUND'
  | 'FREE_TIER_LIMIT'
  | 'HASH_MISMATCH'
  | 'DUPLICATE_ASSET_HASH'
  | 'DUPLICATE_PHOTO_HASH'
  | 'DUPLICATE_PENDING_INVITE'
  | 'ALREADY_MEMBER'
  | 'ALREADY_RESPONDED'
  | 'NOT_OWNER'
  | 'NOT_MEMBER'
  | 'FILE_TOO_LARGE'
  | 'UNSUPPORTED_MEDIA_TYPE'
  | 'INVALID_STATE_TRANSITION'
  | 'EMPTY_REPORT'
  | 'PAYMENT_REQUIRED'
  | 'DOSSIER_EXISTS'
  | 'GENERATION_IN_PROGRESS'
  | 'GENERATION_FAILED'
  | 'AGENT_NOT_VERIFIED'
  | 'SUBSCRIPTION_INACTIVE'
  | 'DUPLICATE_PENDING_INTEREST'
  | 'ALREADY_SHARED'
  | 'ALREADY_DECIDED'
  | 'SHARE_REVOKED'
  | 'PAYMENT_INIT_FAILED'
  | 'INTERNAL_ERROR'
  | 'NETWORK_ERROR';

const MESSAGES: Record<string, string> = {
  VALIDATION_FAILED: 'Please check the highlighted fields.',
  TOKEN_EXPIRED: 'Your session expired. Refreshing…',
  TOKEN_INVALID: 'Your session is no longer valid. Please log in again.',
  REFRESH_INVALID: 'Please log in again.',
  REFRESH_EXPIRED: 'Your session has expired. Please log in again.',
  REFRESH_REUSED: 'For your security we signed you out. Please log in again.',
  OTP_REQUIRED: 'Please verify your phone number to continue.',
  OTP_INVALID: 'That code is incorrect. Please try again.',
  OTP_EXPIRED: 'That code has expired. Request a new one.',
  OTP_THROTTLED: 'Too many attempts. Please wait a moment before trying again.',
  BAD_CREDENTIALS: 'Phone or password is incorrect.',
  PHONE_EXISTS: 'This number already has an account — please log in.',
  LICENCE_EXISTS: 'This licence number is already registered.',
  RATE_LIMITED: 'Too many requests. Please try again shortly.',
  FORBIDDEN: "You don't have access to that.",
  RESOURCE_NOT_FOUND: "We couldn't find that.",
  FREE_TIER_LIMIT: 'The free plan allows one property. Upgrade to PRO to add more.',
  HASH_MISMATCH: 'The photo could not be verified. Please retake and try again.',
  DUPLICATE_ASSET_HASH: 'This exact photo is already documented.',
  DUPLICATE_PHOTO_HASH: 'This exact photo is already on the report.',
  DUPLICATE_PENDING_INVITE: 'An invite is already pending for this person.',
  ALREADY_MEMBER: 'This person is already in the household.',
  ALREADY_RESPONDED: 'This has already been responded to.',
  NOT_OWNER: 'Only the owner can do that.',
  NOT_MEMBER: "You're not a member of this property.",
  FILE_TOO_LARGE: 'That file is too large. Please use a smaller image.',
  UNSUPPORTED_MEDIA_TYPE: 'Only JPEG and PNG images are supported.',
  INVALID_STATE_TRANSITION: 'This report is completed and can no longer be edited.',
  EMPTY_REPORT: 'Add at least one photo before completing the report.',
  PAYMENT_REQUIRED: 'Payment is required to generate this dossier.',
  DOSSIER_EXISTS: 'A dossier already exists for this report.',
  GENERATION_IN_PROGRESS: 'Your dossier is being generated. Hang tight…',
  GENERATION_FAILED: 'Dossier generation failed. You can retry.',
  AGENT_NOT_VERIFIED: 'Your agent account is awaiting verification.',
  SUBSCRIPTION_INACTIVE: 'An active subscription is required.',
  DUPLICATE_PENDING_INTEREST: "You've already expressed interest in this lead.",
  ALREADY_SHARED: 'This dossier is already shared with that agent.',
  ALREADY_DECIDED: 'This has already been decided.',
  SHARE_REVOKED: 'Access to this dossier has been revoked.',
  PAYMENT_INIT_FAILED: 'Payment could not be started. Please try again.',
  INTERNAL_ERROR: 'Something went wrong. Please try again.',
  NETWORK_ERROR: 'No connection. Check your internet and try again.',
};

export class ApiError extends Error {
  code: string;
  httpStatus: number;
  fieldErrors?: Record<string, string>;
  serverMessage?: string;

  constructor(params: { code: string; httpStatus: number; fieldErrors?: Record<string, string>; serverMessage?: string }) {
    super(MESSAGES[params.code] ?? params.serverMessage ?? 'Something went wrong.');
    this.name = 'ApiError';
    this.code = params.code;
    this.httpStatus = params.httpStatus;
    this.fieldErrors = params.fieldErrors;
    this.serverMessage = params.serverMessage;
  }

  is(code: ErrorCode): boolean {
    return this.code === code;
  }
}

export function isApiError(e: unknown): e is ApiError {
  return e instanceof ApiError;
}

/** True for the duplicate-hash codes an offline queue treats as success (§5). */
export function isDuplicateHash(e: unknown): boolean {
  return isApiError(e) && (e.code === 'DUPLICATE_ASSET_HASH' || e.code === 'DUPLICATE_PHOTO_HASH');
}

export function messageFor(code: string, fallback?: string): string {
  return MESSAGES[code] ?? fallback ?? 'Something went wrong.';
}
