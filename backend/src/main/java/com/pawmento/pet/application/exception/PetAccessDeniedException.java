package com.pawmento.pet.application.exception;

public class PetAccessDeniedException extends RuntimeException {

	public PetAccessDeniedException() {
		super("권한이 없습니다.");
	}

}
