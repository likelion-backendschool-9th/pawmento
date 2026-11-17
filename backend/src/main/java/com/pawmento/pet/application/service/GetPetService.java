package com.pawmento.pet.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pawmento.pet.application.port.in.GetPetQuery;
import com.pawmento.pet.application.port.in.GetPetUseCase;
import com.pawmento.pet.application.port.in.PetResult;
import com.pawmento.pet.application.port.out.LoadPetPort;
import com.pawmento.pet.domain.Pet;

@Service
public class GetPetService implements GetPetUseCase {

	private final LoadPetPort loadPetPort;

	public GetPetService(LoadPetPort loadPetPort) {
		this.loadPetPort = loadPetPort;
	}

	@Override
	@Transactional(readOnly = true)
	public PetResult get(GetPetQuery query) {
		Pet pet = PetOwnership.requireOwnedPet(loadPetPort, query.petId(), query.memberId());
		return PetResult.from(pet);
	}

}
