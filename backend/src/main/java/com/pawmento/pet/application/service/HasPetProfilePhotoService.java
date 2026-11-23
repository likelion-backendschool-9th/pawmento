package com.pawmento.pet.application.service;

import org.springframework.stereotype.Service;

import com.pawmento.pet.application.port.in.HasPetProfilePhotoUseCase;
import com.pawmento.pet.application.port.out.LoadPetProfilePhotoReferencePort;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class HasPetProfilePhotoService implements HasPetProfilePhotoUseCase {

	private final LoadPetProfilePhotoReferencePort references;

	@Override
	public boolean exists(Long assetId) {
		return references.existsByProfilePhotoAssetId(assetId);
	}

	@Override
	public boolean existsForPet(Long petId, Long assetId) {
		return references.existsByIdAndProfilePhotoAssetId(petId, assetId);
	}

}
