package com.pawmento.member.application.port.out;

import java.util.Optional;

import com.pawmento.member.domain.EmailVerification;
import com.pawmento.member.domain.VerificationPurpose;

public interface LoadEmailVerificationPort {
    Optional<EmailVerification> load(String email, VerificationPurpose purpose);
}
