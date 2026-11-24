package com.pawmento.family.adapter.out.persistence;

import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.pawmento.family.application.port.out.LoadPetInviteCodePort;
import com.pawmento.family.application.port.out.SavePetInviteCodePort;
import com.pawmento.family.application.port.out.ConsumePetInviteCodePort;
import com.pawmento.family.domain.PetInviteCode;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
class InviteCodePersistenceAdapter implements SavePetInviteCodePort, LoadPetInviteCodePort, ConsumePetInviteCodePort {

    private final PetInviteCodeJpaRepository codeJpaRepository;

    @Override
    public PetInviteCode save(PetInviteCode code) {
        return codeJpaRepository.saveAndFlush(PetInviteCodeJpaEntity.from(code)).toDomain();
    }

    @Override
    public Optional<PetInviteCode> loadByCodeHash(String codeHash) {
        return codeJpaRepository.findByCodeHash(codeHash).map(PetInviteCodeJpaEntity::toDomain);
    }

    @Override
    public boolean consume(String codeHash, Instant now) {
        return codeJpaRepository.deleteUsable(codeHash, now) == 1;
    }

}
