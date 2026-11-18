package com.pawmento.map.domain.exception;

import com.pawmento.global.exception.RuleViolationException;

// 지도 영역의 최솟값이 최댓값보다 크다
public class InvalidMapAreaException extends RuleViolationException {

	public InvalidMapAreaException() {
		super("영역의 최솟값은 최댓값보다 클 수 없습니다.");
	}

}
