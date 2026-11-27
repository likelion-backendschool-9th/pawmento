package com.pawmento.member.adapter.in.web;

import java.time.Instant;

import com.pawmento.member.application.port.in.ConfirmEmailVerificationResult;

public record VerificationTokenResponse(String verificationToken, Instant expiresAt) {

    static VerificationTokenResponse from(ConfirmEmailVerificationResult result) {
        return new VerificationTokenResponse(result.verificationToken(), result.expiresAt());
    }
}
