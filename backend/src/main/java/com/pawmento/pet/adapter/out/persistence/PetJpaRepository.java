package com.pawmento.pet.adapter.out.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PetJpaRepository extends JpaRepository<PetJpaEntity, Long> {

	List<PetJpaEntity> findByOwnerMemberIdOrderByIdAsc(Long ownerMemberId);

	boolean existsByOwnerMemberIdAndTag(Long ownerMemberId, String tag);

}
