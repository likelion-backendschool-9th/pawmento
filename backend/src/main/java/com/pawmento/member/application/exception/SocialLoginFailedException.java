package com.pawmento.member.application.exception;

public class SocialLoginFailedException extends RuntimeException {

    // 제공자가 인가 코드를 거절했거나, 필요한 정보(이메일 등)를 주지 않았다
    public SocialLoginFailedException(String message) {
        super(message);
    }
    
}
