package com.assetshield.property.security;

import java.util.UUID;

/**
 * Authentication principal extracted from the validated JWT. The phone claim
 * is needed to match household invitations addressed to not-yet-registered
 * numbers.
 */
public record AuthUser(UUID id, String role, String phone) {
}
