package com.pawmento.pet.adapter.out.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.pawmento.pet.application.port.out.DeletePetPort;
import com.pawmento.pet.application.port.out.LoadPetPort;
import com.pawmento.pet.application.port.out.SavePetPort;
import com.pawmento.pet.domain.Pet;

@Component
public class PetPersistenceAdapter implements SavePetPort, LoadPetPort, DeletePetPort {

	private final PetJpaRepository petJpaRepository;

	public PetPersistenceAdapter(PetJpaRepository petJpaRepository) {
		this.petJpaRepository = petJpaRepository;
	}

	@Override
	public Long save(Pet pet) {
		PetJpaEntity saved = petJpaRepository.save(PetJpaEntity.from(pet));
		return saved.getId();
	}

	@Override
	public Optional<Pet> findById(Long petId) {
		return petJpaRepository.findById(petId).map(PetJpaEntity::toDomain);
	}

	@Override
	public List<Pet> findByOwnerMemberId(Long ownerMemberId) {
		return petJpaRepository.findByOwnerMemberIdOrderByIdAsc(ownerMemberId)
			.stream()
			.map(PetJpaEntity::toDomain)
			.toList();
	}

	@Override
	public boolean existsByOwnerMemberIdAndTag(Long ownerMemberId, String tag) {
		return petJpaRepository.existsByOwnerMemberIdAndTag(ownerMemberId, tag);
	}

	@Override
	public void deleteById(Long petId) {
		petJpaRepository.deleteById(petId);
	}

}
