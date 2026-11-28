package com.pawmento.member.application.port.in;

import java.time.Instant;

public record ConfirmEmailVerificationResult(String verificationToken, Instant expiresAt, long expiresInSeconds) {
}
