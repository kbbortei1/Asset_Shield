import { isCompleteGhPhone } from '@/components/ui';

/**
 * Client-side pre-validation for the auth forms. Mirrors the backend rules
 * (AuthDtos: +233XXXXXXXXX, password 8-72, fullName 2-120) so users get inline
 * field errors instantly instead of a generic VALIDATION_FAILED round-trip.
 */
export type AuthFields = {
  phoneNumber?: string;
  password?: string;
  fullName?: string;
  insurerName?: string;
  nicLicenceNo?: string;
  code?: string;
};

export function validateAuthFields(
  fields: AuthFields,
  opts: { passwordMinLength?: boolean } = {},
): Record<string, string> {
  const errors: Record<string, string> = {};

  if (fields.phoneNumber !== undefined && !isCompleteGhPhone(fields.phoneNumber)) {
    errors.phoneNumber = 'Enter the 9 digits after +233, e.g. 201112233.';
  }
  if (fields.password !== undefined) {
    if (!fields.password) errors.password = 'Enter your password.';
    else if (opts.passwordMinLength && fields.password.length < 8)
      errors.password = 'Use at least 8 characters.';
  }
  if (fields.fullName !== undefined && fields.fullName.trim().length < 2) {
    errors.fullName = 'Enter your full name.';
  }
  if (fields.insurerName !== undefined && !fields.insurerName.trim()) {
    errors.insurerName = 'Enter your insurer.';
  }
  if (fields.nicLicenceNo !== undefined && !fields.nicLicenceNo.trim()) {
    errors.nicLicenceNo = 'Enter your NIC licence number.';
  }
  if (fields.code !== undefined && !/^\d{6}$/.test(fields.code)) {
    errors.code = 'Enter the 6-digit code.';
  }
  return errors;
}
