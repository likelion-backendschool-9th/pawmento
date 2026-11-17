package com.pawmento.pet.application.exception;

// 도메인 규칙 위반을 HTTP 400 VALIDATION으로 바꿀 때 쓴다
public class InvalidPetException extends RuntimeException {

	public InvalidPetException(String message) {
		super(message);
	}

}
