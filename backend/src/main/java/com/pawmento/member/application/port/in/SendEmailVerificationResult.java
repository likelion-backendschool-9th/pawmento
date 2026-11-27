package com.pawmento.member.application.port.in;

import java.time.Instant;

public record SendEmailVerificationResult(String email, Instant expiresAt, Instant retryAfter) {
}