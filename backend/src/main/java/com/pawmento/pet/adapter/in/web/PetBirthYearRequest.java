package com.pawmento.pet.adapter.in.web;

import com.pawmento.pet.adapter.in.web.exception.InvalidPetBirthYearRequestException;
import tools.jackson.databind.JsonNode;

final class PetBirthYearRequest {

	private PetBirthYearRequest() {
	}

	static Integer parse(JsonNode value) {
		if (value == null || value.isNull()) {
			return null;
		}
		if (!value.isIntegralNumber() || !value.canConvertToInt()) {
			throw new InvalidPetBirthYearRequestException();
		}
		return value.intValue();
	}
}
