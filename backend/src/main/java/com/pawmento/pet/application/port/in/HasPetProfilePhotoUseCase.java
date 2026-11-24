package com.pawmento.pet.application.port.in;

public interface HasPetProfilePhotoUseCase {

	boolean exists(Long assetId);

	boolean existsForPet(Long petId, Long assetId);

}
