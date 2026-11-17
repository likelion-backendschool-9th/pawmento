package com.pawmento.pet.application.exception;

public class PetNotFoundException extends RuntimeException {

	public PetNotFoundException() {
		super("반려동물을 찾을 수 없습니다.");
	}

}
