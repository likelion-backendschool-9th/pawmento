package com.pawmento.family.adapter.out.persistence;

import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.pawmento.family.application.port.out.LoadPetInviteCodePort;
import com.pawmento.family.application.port.out.SavePetInviteCodePort;
import com.pawmento.family.application.port.out.ConsumePetInviteCodePort;
import com.pawmento.family.application.port.out.DeleteExpiredPetInviteCodesPort;
import com.pawmento.family.application.port.out.DeletePetInviteCodesByIssuerPort;
import com.pawmento.family.domain.PetInviteCode;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
class InviteCodePersistenceAdapter implements SavePetInviteCodePort, LoadPetInviteCodePort,
        ConsumePetInviteCodePort, DeleteExpiredPetInviteCodesPort, DeletePetInviteCodesByIssuerPort {

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

    @Override
    public int deleteExpired(Long petId, Instant now) {
        return codeJpaRepository.deleteExpired(petId, now);
    }

    @Override
    public int deleteByIssuer(Long petId, Long issuerMemberId) {
        return codeJpaRepository.deleteByIssuer(petId, issuerMemberId);
    }

}
