package com.pawmento.pet.application.service;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pawmento.pet.application.port.in.EditPetCommand;
import com.pawmento.pet.application.port.in.EditPetUseCase;
import com.pawmento.pet.application.port.in.PetResult;
import com.pawmento.pet.application.port.out.LoadPetPort;
import com.pawmento.pet.application.port.out.SavePetPort;
import com.pawmento.pet.domain.Pet;
import com.pawmento.pet.domain.PetSpecies;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EditPetService implements EditPetUseCase {

	private final LoadPetPort loadPetPort;
	private final SavePetPort savePetPort;
	private final Clock clock;

	@Override
	@Transactional
	public PetResult edit(EditPetCommand command) {
		Pet pet = PetOwnership.requireOwnedPet(loadPetPort, command.petId(), command.memberId());
		PetSpecies species = PetSpeciesParser.optional(command.species());
		LocalDate today = PetDates.today(clock);
		pet.edit(command.name(), species, command.breed(), command.birthday(), command.birthYear(), today);
		savePetPort.save(pet);
		return PetResult.from(pet, today);
	}

}
