package com.pawmento.member.application.exception;

import com.pawmento.global.exception.StateConflictException;

public class SocialEmailConflictException extends StateConflictException {
    
    public SocialEmailConflictException() {
        super("이미 다른 방법으로 가입된 이메일입니다. 처음 가입한 방법으로 로그인해 주세요.");
    }
}
