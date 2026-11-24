package com.pawmento.pet.application.service;

import java.time.Clock;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pawmento.pet.application.port.in.GetPetQuery;
import com.pawmento.pet.application.port.in.GetPetUseCase;
import com.pawmento.pet.application.port.in.PetResult;
import com.pawmento.pet.application.port.out.LoadPetPort;
import com.pawmento.pet.domain.Pet;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GetPetService implements GetPetUseCase {
	private final Clock clock;

	private final LoadPetPort loadPetPort;

	@Override
	@Transactional(readOnly = true)
	public PetResult get(GetPetQuery query) {
		Pet pet = PetOwnership.requireOwnedPet(loadPetPort, query.petId(), query.memberId());
		return PetResult.from(pet, PetDates.today(clock));
	}

}
