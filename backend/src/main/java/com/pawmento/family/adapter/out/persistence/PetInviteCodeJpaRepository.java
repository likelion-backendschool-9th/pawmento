package com.pawmento.family.adapter.out.persistence;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface PetInviteCodeJpaRepository extends JpaRepository<PetInviteCodeJpaEntity, Long> {

    Optional<PetInviteCodeJpaEntity> findByCodeHash(String codeHash);

    @Modifying
    @Query("delete from PetInviteCodeJpaEntity c where c.codeHash = :codeHash and c.expiresAt > :now")
    int deleteUsable(@Param("codeHash") String codeHash, @Param("now") Instant now);

    @Modifying
    @Query("delete from PetInviteCodeJpaEntity c where c.petId = :petId and c.expiresAt <= :now")
    int deleteExpired(@Param("petId") Long petId, @Param("now") Instant now);

}
