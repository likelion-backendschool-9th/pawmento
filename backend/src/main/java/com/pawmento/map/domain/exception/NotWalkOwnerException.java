package com.pawmento.map.domain.exception;

import com.pawmento.global.exception.PermissionDeniedException;

// 산책을 시작한 회원이 아니다. 산책 객체 하나로 판단할 수 있는 규칙이라 도메인 예외다
public class NotWalkOwnerException extends PermissionDeniedException {

	public NotWalkOwnerException() {
		super("다른 회원의 산책은 바꿀 수 없습니다.");
	}

}
