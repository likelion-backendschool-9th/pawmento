package com.pawmento.map.domain.exception;

// 한 산책에 쌓을 수 있는 좌표의 최대 개수를 넘겼다. 응답은 409다.
// 공통 기본 예외(#72)가 develop에 들어오면 StateConflictException을 상속하게 바꾼다
public class WalkPointLimitExceededException extends RuntimeException {

	public WalkPointLimitExceededException(int maxPoints) {
		super("한 산책에는 좌표를 " + maxPoints + "개까지 쌓을 수 있습니다.");
	}

}
