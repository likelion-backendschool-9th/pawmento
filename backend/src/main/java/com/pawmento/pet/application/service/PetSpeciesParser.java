package com.pawmento.pet.application.service;

import com.pawmento.pet.application.exception.InvalidPetException;
import com.pawmento.pet.domain.PetSpecies;

final class PetSpeciesParser {

	private PetSpeciesParser() {
	}

	static PetSpecies require(String species) {
		if (species == null || species.isBlank()) {
			throw InvalidPetException.invalidSpecies();
		}
		try {
			return PetSpecies.valueOf(species);
		}
		catch (IllegalArgumentException e) {
			throw InvalidPetException.invalidSpecies();
		}
	}

	static PetSpecies optional(String species) {
		if (species == null) {
			return null;
		}
		return require(species);
	}

}
