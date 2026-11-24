package com.pawmento.pet.application.port.in;

import java.time.LocalDate;

public record EditPetCommand(
		Long memberId,
		Long petId,
		String name,
		String species,
		String breed,
		LocalDate birthday,
		Integer birthYear) {

	public EditPetCommand(Long memberId, Long petId, String name, String species, String breed, LocalDate birthday) {
		this(memberId, petId, name, species, breed, birthday, null);
	}
}
