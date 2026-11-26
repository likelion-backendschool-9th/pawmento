package com.pawmento.member.application.exception;

import com.pawmento.global.exception.RuleViolationException;

public class UnsupportedVerificationPurposeException extends RuleViolationException {

	public UnsupportedVerificationPurposeException() {
		super("지원하지 않는 인증 용도입니다.");
	}

}