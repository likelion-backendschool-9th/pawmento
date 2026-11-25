package com.pawmento.family.adapter.out.pet;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.pawmento.family.application.port.out.LoadPetOwnerPort;
import com.pawmento.family.application.port.out.LockPetOwnerPort;
import com.pawmento.pet.application.port.in.LockPetOwnerQuery;
import com.pawmento.pet.application.port.in.GetPetOwnerQuery;
import com.pawmento.pet.application.port.in.GetPetOwnerResult;
import com.pawmento.pet.application.port.in.GetPetOwnerUseCase;
import com.pawmento.pet.application.port.in.LockPetOwnerUseCase;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
class PetOwnerAdapter implements LoadPetOwnerPort, LockPetOwnerPort {

    private final GetPetOwnerUseCase getPetOwner;

    private final LockPetOwnerUseCase lockPetOwner;

    @Override
    public Optional<Long> loadOwnerId(Long petId) {
        return getPetOwner.get(new GetPetOwnerQuery(petId)).map(GetPetOwnerResult::ownerMemberId);
    }

    @Override
    public Optional<Long> lockOwnerId(Long petId) {
        return lockPetOwner.lock(new LockPetOwnerQuery(petId)).map(GetPetOwnerResult::ownerMemberId);
    }

}
