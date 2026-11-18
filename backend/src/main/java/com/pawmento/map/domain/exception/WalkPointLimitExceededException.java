package com.pawmento.map.domain.exception;

import com.pawmento.global.exception.StateConflictException;

// 한 산책에 쌓을 수 있는 좌표의 최대 개수를 넘겼다. 응답은 409다.
public class WalkPointLimitExceededException extends StateConflictException {

	public WalkPointLimitExceededException(int maxPoints) {
		super("한 산책에는 좌표를 " + maxPoints + "개까지 쌓을 수 있습니다.");
	}

}
