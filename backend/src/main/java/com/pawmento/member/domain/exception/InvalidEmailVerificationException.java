package com.pawmento.member.domain.exception;

import com.pawmento.global.exception.RuleViolationException;

public class InvalidEmailVerificationException extends RuleViolationException {

	public InvalidEmailVerificationException(String message) {
		super(message);
	}

}