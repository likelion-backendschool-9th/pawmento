package com.pawmento.map.domain.exception;

import com.pawmento.global.exception.RuleViolationException;

// 위도·경도가 범위를 벗어났거나 숫자가 아니다
public class InvalidCoordinatesException extends RuleViolationException {

	private InvalidCoordinatesException(String message) {
		super(message);
	}

	public static InvalidCoordinatesException latitudeOutOfRange() {
		return new InvalidCoordinatesException("위도는 -90 이상 90 이하여야 합니다.");
	}

	public static InvalidCoordinatesException longitudeOutOfRange() {
		return new InvalidCoordinatesException("경도는 -180 이상 180 이하여야 합니다.");
	}

}
