package com.pawmento.community.application.exception;

import com.pawmento.global.exception.StateConflictException;

// 읽은 뒤 다른 요청이 먼저 글을 고치거나 지웠다
public class PostConcurrentModificationException extends StateConflictException {
	public PostConcurrentModificationException() {
		super("다른 요청에서 글을 먼저 고치거나 지웠습니다. 다시 불러와 주세요.");
	}
}
