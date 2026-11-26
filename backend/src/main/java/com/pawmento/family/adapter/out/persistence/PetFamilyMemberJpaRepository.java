package com.pawmento.family.adapter.out.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface  PetFamilyMemberJpaRepository extends JpaRepository<PetFamilyMemberJpaEntity, Long> {

    boolean existsByPetIdAndMemberId(Long petId, Long memberId);

    List<PetFamilyMemberJpaEntity> findByPetIdOrderByJoinedAtAscIdAsc(Long petId);

    List<PetFamilyMemberJpaEntity> findByMemberIdOrderByJoinedAtAscIdAsc(Long memberId);

    @Modifying
    @Query("delete from PetFamilyMemberJpaEntity m where m.petId = :petId and m.memberId = :memberId")
    int deleteByPetIdAndMemberId(@Param("petId") Long petId, @Param("memberId") Long memberId);

}
