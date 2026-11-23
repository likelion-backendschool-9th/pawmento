package com.pawmento.family.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface  PetFamilyMemberJpaRepository extends JpaRepository<PetFamilyMemberJpaEntity, Long> {

    boolean existsByPetIdAndMemberId(Long petId, Long memberId);
    
}
