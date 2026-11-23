package com.pawmento.pet.application.port.out;

import java.util.List;

import com.pawmento.pet.application.port.in.GetPetProfilePhotoUseCase.Photo;

public interface PetProfilePhotoMediaPort {

	void lock(Long memberId, Long petId, List<Long> assetIds);

	void changeReferences(Long memberId, Long petId, List<Long> previous, List<Long> requested);

	Photo read(Long petId, Long assetId, boolean thumbnail);

}
