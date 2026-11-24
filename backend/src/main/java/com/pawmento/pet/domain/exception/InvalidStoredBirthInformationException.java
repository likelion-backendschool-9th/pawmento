package com.pawmento.pet.domain.exception;

public final class InvalidStoredBirthInformationException extends IllegalStateException {
	public InvalidStoredBirthInformationException() {
		super("저장된 생일과 연도 전용 값이 함께 존재합니다.");
	}
}
