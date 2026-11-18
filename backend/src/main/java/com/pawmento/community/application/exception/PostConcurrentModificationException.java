package com.pawmento.community.application.exception;

import com.pawmento.global.exception.StateConflictException;

// 읽은 뒤 다른 요청이 먼저 글을 고치거나 지웠다.
// 저장소의 버전 충돌 예외를 cause로 남긴다. 응답에는 이 안내 문구만 나간다
public class PostConcurrentModificationException extends StateConflictException {
	public PostConcurrentModificationException(Throwable cause) {
		super("다른 요청에서 글을 먼저 고치거나 지웠습니다. 다시 불러와 주세요.", cause);
	}
}
