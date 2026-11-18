package com.pawmento.member.application.exception;

public class SocialEmailConflictException extends RuntimeException {
    
    public SocialEmailConflictException() {
        super("이미 다른 방법으로 가입된 이메일이비낟. 처음 가입한 방법으로 로그인해 주세요. ");
    }
}
