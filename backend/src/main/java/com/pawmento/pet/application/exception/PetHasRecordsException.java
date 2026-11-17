package com.pawmento.pet.application.exception;

public class PetHasRecordsException extends RuntimeException {

	public PetHasRecordsException() {
		super("기록이 있는 반려동물은 삭제할 수 없습니다.");
	}

}
