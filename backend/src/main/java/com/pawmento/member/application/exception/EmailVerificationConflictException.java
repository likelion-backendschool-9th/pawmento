package com.pawmento.member.application.exception;

import com.pawmento.global.exception.StateConflictException;

public class EmailVerificationConflictException extends StateConflictException {

    public EmailVerificationConflictException() {
        super("잠시 후 다시 요청해 주세요.");
    }
}
