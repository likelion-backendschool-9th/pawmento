package com.pawmento.pet.adapter.in.web;

import java.time.LocalDate;

import com.pawmento.pet.application.port.in.PetResult;

public record PetResponse(
		Long petId,
		String name,
		String tag,
		String species,
		String breed,
		LocalDate birthday,
		String mode) {

	static PetResponse from(PetResult result) {
		return new PetResponse(result.petId(), result.name(), result.tag(), result.species(), result.breed(),
				result.birthday(), result.mode());
	}

}
