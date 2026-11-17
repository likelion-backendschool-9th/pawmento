package com.pawmento.pet.application.port.in;

import java.time.LocalDate;

public record CreatePetCommand(
		Long memberId,
		String name,
		String species,
		String breed,
		LocalDate birthday) {
}
