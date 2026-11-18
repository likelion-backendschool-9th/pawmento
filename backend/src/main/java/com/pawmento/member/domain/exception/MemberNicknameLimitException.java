package com.pawmento.member.domain.exception;

import com.pawmento.global.exception.StateConflictException;

public class MemberNicknameLimitException extends StateConflictException {
	public MemberNicknameLimitException() {
		super("닉네임은 한 달에 한 번만 바꿀 수 있어요.");
	}
}
