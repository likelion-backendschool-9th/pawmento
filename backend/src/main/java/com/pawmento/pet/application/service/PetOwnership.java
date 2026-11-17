package com.pawmento.pet.application.service;

import com.pawmento.pet.application.exception.PetAccessDeniedException;
import com.pawmento.pet.application.exception.PetNotFoundException;
import com.pawmento.pet.application.port.out.LoadPetPort;
import com.pawmento.pet.domain.Pet;

// 조회·수정·삭제에서 같은 소유 검사를 쓴다
final class PetOwnership {

	private PetOwnership() {
	}

	static Pet requireOwnedPet(LoadPetPort loadPetPort, Long petId, Long memberId) {
		Pet pet = loadPetPort.findById(petId).orElseThrow(PetNotFoundException::new);
		if (!pet.ownedBy(memberId)) {
			throw new PetAccessDeniedException();
		}
		return pet;
	}

}
