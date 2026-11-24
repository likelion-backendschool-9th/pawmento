package com.pawmento.pet.application.port.out;

public interface LoadPetProfilePhotoReferencePort {

	boolean existsByProfilePhotoAssetId(Long assetId);

	boolean existsByIdAndProfilePhotoAssetId(Long petId, Long assetId);

}
