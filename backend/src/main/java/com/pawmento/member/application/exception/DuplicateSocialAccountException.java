package com.pawmento.member.application.exception;

import com.pawmento.global.exception.StateConflictException;

public class DuplicateSocialAccountException extends StateConflictException {

	public DuplicateSocialAccountException() {
		super("이미 가입된 소셜 계정입니다.");
	}

}