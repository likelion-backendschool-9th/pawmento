package com.pawmento.member.domain;

public class InvalidMemberNicknameException extends RuntimeException {
	public InvalidMemberNicknameException(String message) {
		super(message);
	}
}
