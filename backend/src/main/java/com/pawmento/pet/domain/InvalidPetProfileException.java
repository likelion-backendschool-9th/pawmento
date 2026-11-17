package com.pawmento.pet.domain;

public class InvalidPetProfileException extends RuntimeException {
	public InvalidPetProfileException(String message) {
		super(message);
	}
}
