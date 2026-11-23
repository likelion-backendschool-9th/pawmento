package com.pawmento.pet.application.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pawmento.pet.application.exception.PetProfilePhotoNotFoundException;
import com.pawmento.pet.application.port.in.GetPetProfilePhotoUseCase;
import com.pawmento.pet.application.port.in.PetResult;
import com.pawmento.pet.application.port.in.UpdatePetProfilePhotoUseCase;
import com.pawmento.pet.application.port.out.LoadPetPort;
import com.pawmento.pet.application.port.out.LockPetPort;
import com.pawmento.pet.application.port.out.PetProfilePhotoMediaPort;
import com.pawmento.pet.application.port.out.SavePetPort;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PetProfilePhotoService implements GetPetProfilePhotoUseCase, UpdatePetProfilePhotoUseCase {

	private final LoadPetPort loadPet;
	private final LockPetPort lockPet;
	private final SavePetPort savePet;
	private final PetProfilePhotoMediaPort media;

	@Override
	@Transactional
	public PetResult update(Command command) {
		var pet = PetOwnership.requireUnchangedOwnedPet(loadPet, lockPet, command.petId(), command.memberId());
		Long current = pet.getProfilePhotoAssetId();
		Long requested = command.assetId();
		if (Objects.equals(current, requested)) {
			return PetResult.from(pet);
		}

		List<Long> previous = current == null ? List.of() : List.of(current);
		List<Long> next = requested == null ? List.of() : List.of(requested);
		List<Long> affected = new ArrayList<>(previous);
		affected.addAll(next);
		media.lock(command.memberId(), command.petId(), affected);
		pet.changeProfilePhoto(requested);
		savePet.save(pet);
		media.changeReferences(command.memberId(), command.petId(), previous, next);
		return PetResult.from(pet);
	}

	@Override
	@Transactional(readOnly = true)
	public Photo get(Query query) {
		var pet = PetOwnership.requireOwnedPet(loadPet, query.petId(), query.memberId());
		if (pet.getProfilePhotoAssetId() == null || !pet.getProfilePhotoAssetId().equals(query.assetId())) {
			throw new PetProfilePhotoNotFoundException();
		}
		return media.read(query.petId(), query.assetId(), query.thumbnail());
	}

}
