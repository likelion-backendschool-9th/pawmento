package com.pawmento.map.domain.exception;

import com.pawmento.global.exception.RuleViolationException;

// 한 요청의 좌표 묶음이 비었거나 한 번에 너무 많다. 입력값의 규칙 위반이라 응답은 400이다.
public class InvalidWalkPointBatchException extends RuleViolationException {

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
