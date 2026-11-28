package com.pawmento.member.application.exception;

import com.pawmento.global.exception.RuleViolationException;

public class InvalidVerificationCodeException extends RuleViolationException {

    public InvalidVerificationCodeException() {
        super("인증번호가 맞지 않거나 만료됐어요.");
    }
}
