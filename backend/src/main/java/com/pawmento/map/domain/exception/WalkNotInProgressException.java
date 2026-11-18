package com.pawmento.map.domain.exception;

// 끝났거나 중단된 산책에는 좌표를 쌓을 수 없다. 응답은 409다.
// 공통 기본 예외(#72)가 develop에 들어오면 StateConflictException을 상속하게 바꾼다
public class WalkNotInProgressException extends RuntimeException {

	public WalkNotInProgressException() {
		super("진행 중인 산책이 아니라서 좌표를 쌓을 수 없습니다.");
	}

}
