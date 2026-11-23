package com.pawmento.pet.adapter.in.web;

import com.fasterxml.jackson.annotation.JsonCreator;

import tools.jackson.databind.JsonNode;

public record UpdatePetProfilePhotoRequest(Long assetId) {

	@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
	static UpdatePetProfilePhotoRequest from(JsonNode body) {
		JsonNode id = body.get("assetId");
		if (id == null || !id.isIntegralNumber() || !id.canConvertToLong() || id.longValue() <= 0) {
			throw new IllegalArgumentException("assetId는 양의 정수여야 합니다.");
		}
		return new UpdatePetProfilePhotoRequest(id.longValue());
	}

}
