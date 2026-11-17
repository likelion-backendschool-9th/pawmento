package com.pawmento.pet.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pawmento.pet.application.exception.PetHasRecordsException;
import com.pawmento.pet.application.port.in.DeletePetCommand;
import com.pawmento.pet.application.port.in.DeletePetUseCase;
import com.pawmento.pet.application.port.out.DeletePetPort;
import com.pawmento.pet.application.port.out.LoadPetPort;
import com.pawmento.record.application.port.in.ExistsRecordByPetQuery;
import com.pawmento.record.application.port.in.ExistsRecordByPetUseCase;

@Service
public class DeletePetService implements DeletePetUseCase {

	private final LoadPetPort loadPetPort;
	private final DeletePetPort deletePetPort;
	private final ExistsRecordByPetUseCase existsRecordByPetUseCase;

	public DeletePetService(LoadPetPort loadPetPort, DeletePetPort deletePetPort,
			ExistsRecordByPetUseCase existsRecordByPetUseCase) {
		this.loadPetPort = loadPetPort;
		this.deletePetPort = deletePetPort;
		this.existsRecordByPetUseCase = existsRecordByPetUseCase;
	}

	@Override
	@Transactional
	public void delete(DeletePetCommand command) {
		PetOwnership.requireOwnedPet(loadPetPort, command.petId(), command.memberId());
		if (existsRecordByPetUseCase.exists(new ExistsRecordByPetQuery(command.petId()))) {
			throw new PetHasRecordsException();
		}
		deletePetPort.deleteById(command.petId());
	}

}
