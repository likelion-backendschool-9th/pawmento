package com.pawmento.map.domain.exception;

import com.pawmento.global.exception.StateConflictException;

// 끝났거나 중단된 산책에는 좌표를 쌓을 수 없다. 응답은 409다.
public class WalkNotInProgressException extends StateConflictException {

	public WalkNotInProgressException() {
		super("진행 중인 산책이 아니라서 좌표를 쌓을 수 없습니다.");
	}

}
