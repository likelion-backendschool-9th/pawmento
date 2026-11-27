package com.pawmento.member.application.port.in;

public interface ConfirmEmailVerificationUseCase {
    ConfirmEmailVerificationResult confirm(ConfirmEmailVerificationCommand command);
}
