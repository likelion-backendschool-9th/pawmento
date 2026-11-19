package com.pawmento.member.application.exception;

import com.pawmento.global.exception.RuleViolationException;

public class UnsupportedSocialProviderException extends RuleViolationException {
    
    public UnsupportedSocialProviderException() {
        super("지원하지 않는 로그인 방식입니다.");
    }
}
