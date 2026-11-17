package com.pawmento.pet.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pawmento.pet.application.exception.PetHasRecordsException;
import com.pawmento.pet.application.port.in.DeletePetCommand;
import com.pawmento.pet.application.port.in.DeletePetUseCase;
import com.pawmento.pet.application.port.out.DeletePetPort;
import com.pawmento.pet.application.port.out.HasPetRecordsPort;
import com.pawmento.pet.application.port.out.LoadPetPort;
import com.pawmento.pet.domain.Pet;
import com.pawmento.pet.domain.PetDeletionBlockedException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DeletePetService implements DeletePetUseCase {

	private final LoadPetPort loadPetPort;
	private final DeletePetPort deletePetPort;
	private final HasPetRecordsPort hasPetRecordsPort;

	@Override
	@Transactional
	public void delete(DeletePetCommand command) {
		Pet pet = PetOwnership.requireOwnedPet(loadPetPort, command.petId(), command.memberId());
		try {
			pet.requireDeletable(hasPetRecordsPort.existsByPetId(command.petId()));
		}
		catch (PetDeletionBlockedException e) {
			throw new PetHasRecordsException();
		}
		deletePetPort.deleteById(command.petId());
	}

}
