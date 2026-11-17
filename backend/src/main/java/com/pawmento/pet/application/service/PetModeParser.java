package com.pawmento.pet.application.service;

import com.pawmento.pet.application.exception.InvalidPetException;
import com.pawmento.pet.domain.PetMode;

final class PetModeParser {

	private PetModeParser() {
	}

	static PetMode require(String mode) {
		if (mode == null || mode.isBlank()) {
			throw new InvalidPetException("모드를 선택해 주세요.");
		}
		try {
			return PetMode.valueOf(mode);
		}
		catch (IllegalArgumentException e) {
			throw new InvalidPetException("모드를 선택해 주세요.");
		}
	}

}
