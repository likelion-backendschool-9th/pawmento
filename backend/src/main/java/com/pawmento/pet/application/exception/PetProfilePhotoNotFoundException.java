package com.pawmento.pet.application.exception;

import com.pawmento.global.exception.ResourceNotFoundException;

public class PetProfilePhotoNotFoundException extends ResourceNotFoundException {

	public PetProfilePhotoNotFoundException() {
		super("반려동물 프로필 사진을 찾을 수 없습니다.");
	}

}
