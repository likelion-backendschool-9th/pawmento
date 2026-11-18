package com.pawmento.pet.application.service;

import com.pawmento.pet.application.exception.InvalidPetException;
import com.pawmento.pet.domain.PetMode;

final class PetModeParser {

	private PetModeParser() {
	}

	static PetMode require(String mode) {
		if (mode == null || mode.isBlank()) {
			throw InvalidPetException.invalidMode();
		}
		try {
			return PetMode.valueOf(mode);
		}
		catch (IllegalArgumentException e) {
			throw InvalidPetException.invalidMode();
		}
	}

}
