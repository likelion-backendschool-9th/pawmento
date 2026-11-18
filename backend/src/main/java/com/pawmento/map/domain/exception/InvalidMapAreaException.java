package com.pawmento.map.domain.exception;

import com.pawmento.global.exception.RuleViolationException;

// 지도 영역의 최솟값이 최댓값보다 크다
public class InvalidMapAreaException extends RuleViolationException {

	public InvalidMapAreaException(String message) {
		super(message);
	}

}
