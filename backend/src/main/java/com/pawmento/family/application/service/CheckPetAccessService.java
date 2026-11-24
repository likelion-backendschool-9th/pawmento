package com.pawmento.family.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pawmento.family.application.port.in.CheckPetAccessQuery;
import com.pawmento.family.application.port.in.CheckPetAccessResult;
import com.pawmento.family.application.port.in.CheckPetAccessUseCase;
import com.pawmento.family.application.port.out.LoadPetFamilyMemberPort;
import com.pawmento.family.application.port.out.LoadPetOwnerPort;
import com.pawmento.family.domain.PetAccess;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CheckPetAccessService implements CheckPetAccessUseCase {

    private final LoadPetOwnerPort loadPetOwnerPort;

    private final LoadPetFamilyMemberPort loadPetFamilyMemberPort;

    @Override
    @Transactional(readOnly = true)
    public CheckPetAccessResult check(CheckPetAccessQuery query) {
        PetAccess access = loadPetOwnerPort.loadOwnerId(query.petId())
                    .map(ownerId -> decide(ownerId, query))
                    .orElse(PetAccess.NONE);
        return CheckPetAccessResult.from(access);
    }

    private PetAccess decide(Long ownerId, CheckPetAccessQuery query) {
        if (ownerId.equals(query.memberId())) {
            return PetAccess.OWNER;
        }
        if (loadPetFamilyMemberPort.existsByPetIdAndMemberId(query.petId(), query.memberId())) {
            return PetAccess.FAMILY;
        }
        return PetAccess.NONE;
    }
}
