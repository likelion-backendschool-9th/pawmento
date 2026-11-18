package com.pawmento.pet.application.exception;

import com.pawmento.global.exception.ResourceNotFoundException;

public class PetNotFoundException extends ResourceNotFoundException {

	public PetNotFoundException() {
		super("반려동물을 찾을 수 없습니다.");
	}

}
