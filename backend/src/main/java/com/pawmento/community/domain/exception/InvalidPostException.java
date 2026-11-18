package com.pawmento.community.domain.exception;

import com.pawmento.global.exception.RuleViolationException;

// 글의 입력 규칙(작성자, 카테고리, 제목·본문 길이)을 어겼다
public class InvalidPostException extends RuleViolationException {
	public InvalidPostException(String message) {
		super(message);
	}
}
