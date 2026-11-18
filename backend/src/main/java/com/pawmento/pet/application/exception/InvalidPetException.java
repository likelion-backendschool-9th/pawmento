package com.pawmento.pet.application.exception;

import com.pawmento.global.exception.RuleViolationException;

public class InvalidPetException extends RuleViolationException {

	private InvalidPetException(String message) {
		super(message);
	}

	public static InvalidPetException invalidSpecies() {
		return new InvalidPetException("반려동물의 종류를 선택해 주세요.");
	}

	public static InvalidPetException invalidMode() {
		return new InvalidPetException("모드를 선택해 주세요.");
	}

}
