package com.pawmento.map.domain.exception;

import com.pawmento.global.exception.RuleViolationException;

// 기록 위치의 입력 규칙(장소 이름 길이)을 어겼다
public class InvalidRecordLocationException extends RuleViolationException {

	public InvalidRecordLocationException(String message) {
		super(message);
	}

}
