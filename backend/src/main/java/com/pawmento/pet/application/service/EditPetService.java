package com.pawmento.pet.application.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pawmento.pet.application.exception.InvalidPetException;
import com.pawmento.pet.application.port.in.EditPetCommand;
import com.pawmento.pet.application.port.in.EditPetUseCase;
import com.pawmento.pet.application.port.in.PetResult;
import com.pawmento.pet.application.port.out.LoadPetPort;
import com.pawmento.pet.application.port.out.SavePetPort;
import com.pawmento.pet.domain.Pet;
import com.pawmento.pet.domain.PetSpecies;

@Service
public class EditPetService implements EditPetUseCase {

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	private final LoadPetPort loadPetPort;
	private final SavePetPort savePetPort;
	private final Clock clock;

	public EditPetService(LoadPetPort loadPetPort, SavePetPort savePetPort, Clock clock) {
		this.loadPetPort = loadPetPort;
		this.savePetPort = savePetPort;
		this.clock = clock;
	}

	@Override
	@Transactional
	public PetResult edit(EditPetCommand command) {
		Pet pet = PetOwnership.requireOwnedPet(loadPetPort, command.petId(), command.memberId());
		PetSpecies species = PetSpeciesParser.optional(command.species());
		LocalDate today = LocalDate.now(clock.withZone(SEOUL));
		try {
			pet.edit(command.name(), species, command.breed(), command.birthday(), today);
		}
		catch (IllegalArgumentException e) {
			throw InvalidPetException.from(e);
		}
		savePetPort.save(pet);
		return PetResult.from(pet);
	}

}
