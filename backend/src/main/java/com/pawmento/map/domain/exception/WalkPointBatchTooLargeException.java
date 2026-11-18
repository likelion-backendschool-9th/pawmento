package com.pawmento.map.domain.exception;

// 한 요청에 보낸 좌표가 너무 많다. 입력값의 규칙 위반이라 응답은 400이다.
// 공통 기본 예외(#72)가 develop에 들어오면 RuleViolationException을 상속하게 바꾼다
public class WalkPointBatchTooLargeException extends RuntimeException {

	public WalkPointBatchTooLargeException(int maxPerRequest) {
		super("좌표는 한 번에 " + maxPerRequest + "개까지 보낼 수 있습니다.");
	}

}
