package com.pawmento.pet.application.service;

import com.pawmento.pet.application.exception.InvalidPetException;
import com.pawmento.pet.domain.PetSpecies;

final class PetSpeciesParser {

	private PetSpeciesParser() {
	}

	static PetSpecies require(String species) {
		if (species == null || species.isBlank()) {
			throw new InvalidPetException("반려동물의 종류를 선택해 주세요.");
		}
		try {
			return PetSpecies.valueOf(species);
		}
		catch (IllegalArgumentException e) {
			throw new InvalidPetException("반려동물의 종류를 선택해 주세요.");
		}
	}

	static PetSpecies optional(String species) {
		if (species == null) {
			return null;
		}
		return require(species);
	}

}
