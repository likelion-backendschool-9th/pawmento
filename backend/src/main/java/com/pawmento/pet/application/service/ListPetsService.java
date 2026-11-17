package com.pawmento.pet.application.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pawmento.pet.application.port.in.ListPetsQuery;
import com.pawmento.pet.application.port.in.ListPetsUseCase;
import com.pawmento.pet.application.port.in.PetResult;
import com.pawmento.pet.application.port.out.LoadPetPort;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ListPetsService implements ListPetsUseCase {

	private final LoadPetPort loadPetPort;

	@Override
	@Transactional(readOnly = true)
	public List<PetResult> list(ListPetsQuery query) {
		return loadPetPort.findByOwnerMemberId(query.memberId()).stream().map(PetResult::from).toList();
	}

}
