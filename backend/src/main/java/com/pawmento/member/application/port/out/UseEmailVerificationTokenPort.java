package com.pawmento.member.application.port.out;

import java.time.Instant;

import com.pawmento.member.domain.VerificationPurpose;

public interface UseEmailVerificationTokenPort {

    boolean use(String email, VerificationPurpose purpose, String tokenHash, Instant now);
}
