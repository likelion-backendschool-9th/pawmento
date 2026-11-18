package com.pawmento.map.application.exception;

import com.pawmento.global.exception.StateConflictException;

// 반려동물에게 이미 진행 중인 산책이 있다. 지금 상태 때문에 할 수 없으므로 409다
public class WalkAlreadyInProgressException extends StateConflictException {

	public WalkAlreadyInProgressException() {
		super("이미 진행 중인 산책이 있습니다.");
	}

}
