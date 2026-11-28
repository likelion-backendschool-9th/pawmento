package com.pawmento.member.application.port.out;

import com.pawmento.member.domain.EmailVerification;

public interface ConfirmEmailVerificationPort {
    boolean confirm(EmailVerification confirmed);
}
