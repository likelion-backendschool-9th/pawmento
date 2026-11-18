package com.pawmento.member.domain.exception;

import com.pawmento.global.exception.RuleViolationException;

public class InvalidMemberNicknameException extends RuleViolationException {
	public InvalidMemberNicknameException() {
		super("닉네임은 1~20자로 입력해 주세요.");
	}
}