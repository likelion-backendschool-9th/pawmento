package com.pawmento.member.application.port.out;

import java.time.Instant;

import com.pawmento.member.domain.EmailVerification;

public interface ResendEmailVerificationPort {
    boolean resend(EmailVerification fresh, Instant lastSentAtOrBefore);
}
