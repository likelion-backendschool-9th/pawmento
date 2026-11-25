package com.pawmento.family.adapter.out.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

interface  PetFamilyMemberJpaRepository extends JpaRepository<PetFamilyMemberJpaEntity, Long> {

    boolean existsByPetIdAndMemberId(Long petId, Long memberId);

    List<PetFamilyMemberJpaEntity> findByPetIdOrderByJoinedAtAscIdAsc(Long petId);

}
