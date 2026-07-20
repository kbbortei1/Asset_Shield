/** Backend response envelope — every response, success or error (handoff §3). */
export type Envelope<T> = {
  status: 'success' | 'error';
  data: T;
  message: string;
};

export type ErrorData = {
  errorCode: string;
  /**
   * Per-field details. The backend emits `fields` (VALIDATION_FAILED per-field
   * messages, and context like the existing dossierId on DOSSIER_EXISTS);
   * `fieldErrors` kept for tolerance.
   */
  fields?: Record<string, string>;
  fieldErrors?: Record<string, string>;
};

/** Paginated list shape (handoff §3). */
export type Page<T> = {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export type PageParams = { page?: number; size?: number };
