package com.pawmento.pet.domain;

public class PetOwnershipViolationException extends RuntimeException {
	public PetOwnershipViolationException() {
		super("권한이 없습니다.");
	}
}
