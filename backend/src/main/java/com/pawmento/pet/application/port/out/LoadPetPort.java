package com.pawmento.pet.application.port.out;

import java.util.List;
import java.util.Optional;

import com.pawmento.pet.domain.Pet;

public interface LoadPetPort {

	Optional<Pet> findById(Long petId);

	List<Pet> findByOwnerMemberId(Long ownerMemberId);

	boolean existsByOwnerMemberIdAndTag(Long ownerMemberId, String tag);

}
