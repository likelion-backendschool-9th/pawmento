package com.pawmento.pet.application.service;

import java.util.Objects;

import com.pawmento.pet.application.exception.PetConcurrentModificationException;
import com.pawmento.pet.application.exception.PetNotFoundException;
import com.pawmento.pet.application.port.out.LoadPetPort;
import com.pawmento.pet.application.port.out.LockPetPort;
import com.pawmento.pet.domain.Pet;

// 조회·수정·삭제에서 같은 소유 검사를 쓴다
final class PetOwnership {

	private PetOwnership() {
	}

	static Pet requireOwnedPet(LoadPetPort loadPetPort, Long petId, Long memberId) {
		Pet pet = loadPetPort.findById(petId).orElseThrow(PetNotFoundException::new);
		pet.requireOwnedBy(memberId);
		return pet;
	}

	static Pet requireUnchangedOwnedPet(LoadPetPort loadPetPort, LockPetPort lockPetPort, Long petId, Long memberId) {
		Pet initial = requireOwnedPet(loadPetPort, petId, memberId);
		Pet locked = lockPetPort.lock(petId).orElseThrow(PetConcurrentModificationException::new);
		locked.requireOwnedBy(memberId);
		if (!Objects.equals(initial.getVersion(), locked.getVersion())) {
			throw new PetConcurrentModificationException();
		}
		return locked;
	}

}
