package com.pawmento.pet.adapter.in.web;

import java.time.LocalDate;

import com.pawmento.global.web.ApiPaths;
import com.pawmento.pet.application.port.in.PetResult;

public record PetResponse(
		Long petId,
		String name,
		String tag,
		String species,
		String breed,
		LocalDate birthday,
		String mode,
		ProfilePhoto profilePhoto) {

	static PetResponse from(PetResult result) {
		ProfilePhoto photo = null;
		if (result.profilePhotoAssetId() != null) {
			Long assetId = result.profilePhotoAssetId();
			String path = ApiPaths.V1 + "/pets/" + result.petId() + "/profile-photo/" + assetId;
			photo = new ProfilePhoto(assetId, path + "/content", path + "/thumbnail");
		}

		return new PetResponse(result.petId(), result.name(), result.tag(), result.species(), result.breed(),
				result.birthday(), result.mode(), photo);
	}

	public record ProfilePhoto(Long assetId, String contentUrl, String thumbnailUrl) {
	}

}
