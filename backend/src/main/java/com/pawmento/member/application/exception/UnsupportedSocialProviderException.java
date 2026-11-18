package com.pawmento.member.application.exception;

public class UnsupportedSocialProviderException extends RuntimeException {
    
    public UnsupportedSocialProviderException() {
        super("지원하지 않는 로그인 방식입니다.");
    }
}
