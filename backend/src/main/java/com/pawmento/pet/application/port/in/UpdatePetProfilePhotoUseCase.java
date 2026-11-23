package com.pawmento.pet.application.port.in;

public interface UpdatePetProfilePhotoUseCase {

	PetResult update(Command command);

	record Command(Long memberId, Long petId, Long assetId) {
	}

}
