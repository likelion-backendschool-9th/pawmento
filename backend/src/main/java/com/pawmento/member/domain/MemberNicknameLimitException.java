package com.pawmento.member.domain;

public class MemberNicknameLimitException extends RuntimeException {
	public MemberNicknameLimitException(String message) {
		super(message);
	}
}
