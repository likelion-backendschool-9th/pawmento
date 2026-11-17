package com.pawmento.member.application.exception;

// 이메일이 없는 경우와 비밀번호가 틀린 경우를 구분하지 않는다. 가입된 이메일인지 알려 주지 않기 위해서다
public class InvalidCredentialsException extends RuntimeException {

	public InvalidCredentialsException() {
		super("이메일 또는 비밀번호가 올바르지 않습니다.");
	}

}
