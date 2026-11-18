package com.pawmento.member.application.exception;

public class DuplicateSocialAccountException extends RuntimeException {

    public DuplicateSocialAccountException() {
        super("이미 가입된 소셜 계정입니다.");
    }
    
}
