package com.pawmento.pet.application.port.out;

import java.util.List;

import com.pawmento.pet.application.port.in.GetPetProfilePhotoUseCase.Photo;

public interface PetProfilePhotoMediaPort {

	/** previous는 소유권을 확인한 뒤 잠근 반려동물 행에서 읽은 현재 연결이다. */
	void lock(Long memberId, Long petId, List<Long> previous, List<Long> requested);

	void changeReferences(Long memberId, Long petId, List<Long> previous, List<Long> requested);

	Photo read(Long petId, Long assetId, boolean thumbnail);

}
