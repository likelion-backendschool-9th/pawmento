package com.pawmento.map.application.exception;

import com.pawmento.global.exception.PermissionDeniedException;

// 다른 회원의 산책이다
public class WalkAccessDeniedException extends PermissionDeniedException {

	public WalkAccessDeniedException() {
		super("다른 회원의 산책은 바꿀 수 없습니다.");
	}

}
