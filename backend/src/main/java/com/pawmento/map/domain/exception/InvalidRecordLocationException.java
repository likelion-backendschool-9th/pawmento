package com.pawmento.map.domain.exception;

import com.pawmento.global.exception.RuleViolationException;

// 기록 위치의 입력 규칙(장소 이름 길이)을 어겼다
public class InvalidRecordLocationException extends RuleViolationException {

	private InvalidRecordLocationException(String message) {
		super(message);
	}

	public static InvalidRecordLocationException placeNameTooLong(int maxLength) {
		return new InvalidRecordLocationException("장소 이름은 " + maxLength + "자 이하여야 합니다.");
	}

}
