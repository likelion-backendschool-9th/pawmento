package com.pawmento.member.application.exception;

import com.pawmento.global.exception.StateConflictException;

public class DuplicateEmailException extends StateConflictException {

	public DuplicateEmailException() {
		super("이미 가입된 이메일입니다.");
	}

}
