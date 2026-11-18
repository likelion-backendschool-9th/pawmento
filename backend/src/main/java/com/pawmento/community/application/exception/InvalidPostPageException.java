package com.pawmento.community.application.exception;

import com.pawmento.global.exception.RuleViolationException;

// 목록 크기(size)나 커서(cursor)가 허용 범위 밖
public class InvalidPostPageException extends RuleViolationException {
	public InvalidPostPageException(String message) {
		super(message);
	}
}
