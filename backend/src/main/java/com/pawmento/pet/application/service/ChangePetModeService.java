package com.pawmento.pet.application.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pawmento.pet.application.port.in.ChangePetModeCommand;
import com.pawmento.pet.application.port.in.ChangePetModeUseCase;
import com.pawmento.pet.application.port.in.PetResult;
import com.pawmento.pet.application.port.out.LoadPetPort;
import com.pawmento.pet.application.port.out.SavePetPort;
import com.pawmento.pet.domain.Pet;
import com.pawmento.pet.domain.PetMode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ChangePetModeService implements ChangePetModeUseCase {
	private final Clock clock;

	private final LoadPetPort loadPetPort;
	private final SavePetPort savePetPort;

	@Override
	@Transactional
	public PetResult changeMode(ChangePetModeCommand command) {
		Pet pet = PetOwnership.requireOwnedPet(loadPetPort, command.petId(), command.memberId());
		PetMode mode = PetModeParser.require(command.mode());
		pet.changeMode(mode);
		savePetPort.save(pet);
		return PetResult.from(pet, LocalDate.now(clock.withZone(ZoneId.of("Asia/Seoul"))));
	}

}
