package com.pawmento.community.application.exception;

import com.pawmento.global.exception.RuleViolationException;

// 없는 카테고리 코드. 언제 보내도 실패하므로 400이다
public class InvalidPostCategoryException extends RuleViolationException {
	public InvalidPostCategoryException() {
		super("카테고리를 확인해 주세요.");
	}
}
