package com.pawmento.member.application.exception;

import com.pawmento.global.exception.ResourceNotFoundException;

public class MemberNotFoundException extends ResourceNotFoundException {

	public MemberNotFoundException() {
		super("회원을 찾을 수 없습니다.");
	}

}
