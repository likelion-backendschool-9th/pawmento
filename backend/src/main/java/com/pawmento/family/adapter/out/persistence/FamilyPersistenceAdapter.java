package com.pawmento.family.adapter.out.persistence;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.pawmento.family.application.port.out.LoadPetFamilyMemberPort;
import com.pawmento.family.application.port.out.SavePetFamilyMemberPort;
import com.pawmento.family.domain.PetFamilyMember;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
class FamilyPersistenceAdapter implements LoadPetFamilyMemberPort, SavePetFamilyMemberPort {

    private final PetFamilyMemberJpaRepository memberRepository;

    @Override
    public boolean existsByPetIdAndMemberId(Long petId, Long memberId) {
        return memberRepository.existsByPetIdAndMemberId(petId, memberId);
    }

    @Override
    public PetFamilyMember save(PetFamilyMember member) {
        return memberRepository.saveAndFlush(PetFamilyMemberJpaEntity.from(member)).toDomain();
    }

    @Override
    public List<PetFamilyMember> loadByPetId(Long petId) {
        return memberRepository.findByPetIdOrderByJoinedAtAscIdAsc(petId).stream()
                .map(PetFamilyMemberJpaEntity::toDomain)
                .toList();
    }

    @Override
    public List<PetFamilyMember> loadByMemberId(Long memberId) {
        return memberRepository.findByMemberIdOrderByJoinedAtAscIdAsc(memberId).stream()
                .map(PetFamilyMemberJpaEntity::toDomain)
                .toList();
    }
}
