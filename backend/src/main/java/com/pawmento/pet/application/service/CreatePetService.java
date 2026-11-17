package com.pawmento.pet.application.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pawmento.pet.application.exception.InvalidPetException;
import com.pawmento.pet.application.port.in.CreatePetCommand;
import com.pawmento.pet.application.port.in.CreatePetUseCase;
import com.pawmento.pet.application.port.in.PetResult;
import com.pawmento.pet.application.port.out.SavePetPort;
import com.pawmento.pet.domain.InvalidPetProfileException;
import com.pawmento.pet.domain.Pet;
import com.pawmento.pet.domain.PetSpecies;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CreatePetService implements CreatePetUseCase {

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	private final SavePetPort savePetPort;
	private final PetTagGenerator petTagGenerator;
	private final Clock clock;

	@Override
	@Transactional
	public PetResult create(CreatePetCommand command) {
		PetSpecies species = PetSpeciesParser.require(command.species());
		String tag = petTagGenerator.nextTag(command.memberId());
		LocalDate today = LocalDate.now(clock.withZone(SEOUL));
		Pet pet;
		try {
			pet = Pet.create(command.memberId(), command.name(), tag, species, command.breed(), command.birthday(),
					today);
		}
		catch (InvalidPetProfileException e) {
			throw new InvalidPetException(e.getMessage());
		}
		Long petId = savePetPort.save(pet);
		return PetResult.from(petId, pet);
	}

}
