package com.pawmento.community.application.exception;

import com.pawmento.global.exception.StateConflictException;

// 사용하지 않는 카테고리. 다시 켜면 성공하므로 409다
public class InactivePostCategoryException extends StateConflictException {
	public InactivePostCategoryException() {
		super("지금은 사용하지 않는 카테고리입니다.");
	}
}
