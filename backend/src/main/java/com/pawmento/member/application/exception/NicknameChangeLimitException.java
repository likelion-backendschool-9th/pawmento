package com.pawmento.member.application.exception;

public class NicknameChangeLimitException extends RuntimeException {

	public NicknameChangeLimitException() {
		super("닉네임은 한 달에 한 번만 바꿀 수 있어요.");
	}

}
