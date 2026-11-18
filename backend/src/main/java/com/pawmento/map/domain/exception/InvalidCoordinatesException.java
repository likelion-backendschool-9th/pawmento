package com.pawmento.map.domain.exception;

import com.pawmento.global.exception.RuleViolationException;

// 위도·경도가 허용 범위를 벗어났거나 숫자가 아니다
public class InvalidCoordinatesException extends RuleViolationException {

	public InvalidCoordinatesException(String message) {
		super(message);
	}

}
