package com.pawmento.map.domain.exception;

// 한 요청의 좌표 묶음이 비었거나 한 번에 너무 많다. 입력값의 규칙 위반이라 응답은 400이다.
// 공통 기본 예외(#72)가 develop에 들어오면 RuleViolationException을 상속하게 바꾼다
public class InvalidWalkPointBatchException extends RuntimeException {

	private InvalidWalkPointBatchException(String message) {
		super(message);
	}

	public static InvalidWalkPointBatchException empty() {
		return new InvalidWalkPointBatchException("좌표를 한 개 이상 보내 주세요.");
	}

	public static InvalidWalkPointBatchException tooLarge(int maxPerRequest) {
		return new InvalidWalkPointBatchException("좌표는 한 번에 " + maxPerRequest + "개까지 보낼 수 있습니다.");
	}

}
