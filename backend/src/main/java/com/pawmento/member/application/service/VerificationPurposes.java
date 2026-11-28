package com.pawmento.member.application.service;

import com.pawmento.member.application.exception.UnsupportedVerificationPurposeException;
import com.pawmento.member.domain.VerificationPurpose;

final class VerificationPurposes {

    private VerificationPurposes() {
    }

    static VerificationPurpose open(String value) {
        if (!VerificationPurpose.SIGNUP.name().equals(value)) {
            throw new UnsupportedVerificationPurposeException();
        }
        return VerificationPurpose.SIGNUP;
    }
}
