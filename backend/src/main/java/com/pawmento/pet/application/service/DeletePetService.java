package com.pawmento.pet.application.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pawmento.pet.application.exception.PetHasRecordsException;
import com.pawmento.pet.application.port.in.DeletePetCommand;
import com.pawmento.pet.application.port.in.DeletePetUseCase;
import com.pawmento.pet.application.port.out.DeletePetPort;
import com.pawmento.pet.application.port.out.HasPetRecordsPort;
import com.pawmento.pet.application.port.out.LoadPetPort;
import com.pawmento.pet.application.port.out.LockPetPort;
import com.pawmento.pet.application.port.out.PetProfilePhotoMediaPort;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DeletePetService implements DeletePetUseCase {

	private final LoadPetPort loadPetPort;
	private final DeletePetPort deletePetPort;
	private final HasPetRecordsPort hasPetRecordsPort;
	private final LockPetPort lockPetPort;
	private final PetProfilePhotoMediaPort media;

	@Override
	@Transactional
	public void delete(DeletePetCommand command) {
		var pet = PetOwnership.requireUnchangedOwnedPet(loadPetPort, lockPetPort, command.petId(), command.memberId());
		if (hasPetRecordsPort.existsByPetId(command.petId())) {
			throw new PetHasRecordsException();
		}
		Long assetId = pet.getProfilePhotoAssetId();
		if (assetId == null) {
			deletePetPort.deleteById(command.petId());
			return;
		}

		List<Long> previous = List.of(assetId);
		media.lock(command.memberId(), command.petId(), previous);
		deletePetPort.deleteById(command.petId());
		media.changeReferences(command.memberId(), command.petId(), previous, List.of());
	}

}
