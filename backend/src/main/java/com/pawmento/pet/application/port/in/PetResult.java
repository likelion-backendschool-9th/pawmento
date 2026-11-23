package com.pawmento.pet.application.port.in;

import java.time.LocalDate;

import com.pawmento.pet.domain.Pet;

public record PetResult(
		Long petId,
		String name,
		String tag,
		String species,
		String breed,
		LocalDate birthday,
		String mode,
		Long profilePhotoAssetId) {

	public static PetResult from(Long petId, Pet pet) {
		return new PetResult(petId, pet.getName(), pet.getTag(), pet.getSpecies().name(), pet.getBreed(),
				pet.getBirthday(), pet.getMode().name(), pet.getProfilePhotoAssetId());
	}

	public static PetResult from(Pet pet) {
		return from(pet.getId(), pet);
	}

}
