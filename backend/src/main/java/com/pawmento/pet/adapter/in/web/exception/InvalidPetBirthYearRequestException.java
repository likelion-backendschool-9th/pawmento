package com.pawmento.pet.adapter.in.web.exception;

import com.pawmento.global.exception.RuleViolationException;

public class InvalidPetBirthYearRequestException extends RuleViolationException {
	public InvalidPetBirthYearRequestException() {
		super("출생 연도는 JSON 정수로 입력해 주세요.");
	}
}
