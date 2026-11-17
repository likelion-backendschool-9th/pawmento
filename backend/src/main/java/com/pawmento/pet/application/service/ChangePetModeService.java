package com.pawmento.pet.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pawmento.pet.application.exception.InvalidPetException;
import com.pawmento.pet.application.port.in.ChangePetModeCommand;
import com.pawmento.pet.application.port.in.ChangePetModeUseCase;
import com.pawmento.pet.application.port.in.PetResult;
import com.pawmento.pet.application.port.out.LoadPetPort;
import com.pawmento.pet.application.port.out.SavePetPort;
import com.pawmento.pet.domain.Pet;
import com.pawmento.pet.domain.PetMode;

@Service
public class ChangePetModeService implements ChangePetModeUseCase {

	private final LoadPetPort loadPetPort;
	private final SavePetPort savePetPort;

	public ChangePetModeService(LoadPetPort loadPetPort, SavePetPort savePetPort) {
		this.loadPetPort = loadPetPort;
		this.savePetPort = savePetPort;
	}

	@Override
	@Transactional
	public PetResult changeMode(ChangePetModeCommand command) {
		Pet pet = PetOwnership.requireOwnedPet(loadPetPort, command.petId(), command.memberId());
		PetMode mode = PetModeParser.require(command.mode());
		try {
			pet.changeMode(mode);
		}
		catch (IllegalArgumentException e) {
			throw InvalidPetException.from(e);
		}
		savePetPort.save(pet);
		return PetResult.from(pet);
	}

}
