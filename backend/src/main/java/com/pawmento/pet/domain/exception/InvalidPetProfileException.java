package com.pawmento.pet.domain.exception;

import com.pawmento.global.exception.RuleViolationException;

public class InvalidPetProfileException extends RuleViolationException {
	private InvalidPetProfileException(String message) {
		super(message);
	}

	public static InvalidPetProfileException invalidMode() {
		return new InvalidPetProfileException("모드를 선택해 주세요.");
	}

	public static InvalidPetProfileException missingOwner() {
		return new InvalidPetProfileException("소유자가 필요합니다.");
	}

	public static InvalidPetProfileException missingTag() {
		return new InvalidPetProfileException("태그가 필요합니다.");
	}

	public static InvalidPetProfileException invalidName() {
		return new InvalidPetProfileException("이름은 1~20자로 입력해 주세요.");
	}

	public static InvalidPetProfileException invalidSpecies() {
		return new InvalidPetProfileException("반려동물의 종류를 선택해 주세요.");
	}

	public static InvalidPetProfileException futureBirthday() {
		return new InvalidPetProfileException("생일은 오늘 이전으로 입력해 주세요.");
	}

}
