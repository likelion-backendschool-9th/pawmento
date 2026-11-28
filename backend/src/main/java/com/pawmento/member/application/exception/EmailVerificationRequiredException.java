package com.pawmento.member.application.exception;

import com.pawmento.global.exception.RuleViolationException;

public class EmailVerificationRequiredException extends RuleViolationException {

    public EmailVerificationRequiredException() {
        super("이메일 인증을 다시 해 주세요.");
    }
}
