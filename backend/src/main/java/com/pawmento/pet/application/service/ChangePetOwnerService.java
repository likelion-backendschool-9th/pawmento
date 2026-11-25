package com.pawmento.pet.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.pawmento.pet.application.exception.PetConcurrentModificationException;
import com.pawmento.pet.application.exception.PetNotFoundException;
import com.pawmento.pet.application.port.in.ChangePetOwnerCommand;
import com.pawmento.pet.application.port.in.ChangePetOwnerUseCase;
import com.pawmento.pet.application.port.out.LoadPetPort;
import com.pawmento.pet.application.port.out.LockPetPort;
import com.pawmento.pet.application.port.out.SavePetPort;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ChangePetOwnerService implements ChangePetOwnerUseCase {

	private final LockPetPort lockPetPort;
	private final LoadPetPort loadPetPort;
	private final SavePetPort savePetPort;
	private final PetTagGenerator petTagGenerator;

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public void changeOwner(ChangePetOwnerCommand command) {
		var pet = lockPetPort.lock(command.petId()).orElseThrow(PetNotFoundException::new);
		if (!pet.ownedBy(command.currentOwnerId())) {
			throw new PetConcurrentModificationException();
		}
		if (pet.ownedBy(command.newOwnerId())) {
			return;
		}

		String tag = pet.getTag();
		if (loadPetPort.existsByOwnerMemberIdAndTag(command.newOwnerId(), tag)) {
			tag = petTagGenerator.nextTag(command.newOwnerId());
		}
		pet.changeOwner(command.newOwnerId(), tag);
		savePetPort.save(pet);
	}

}
