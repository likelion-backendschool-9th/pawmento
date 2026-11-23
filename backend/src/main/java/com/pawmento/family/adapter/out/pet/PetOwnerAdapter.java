package com.pawmento.family.adapter.out.pet;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.pawmento.family.application.port.out.LoadPetOwnerPort;
import com.pawmento.pet.application.port.in.GetPetOwnerQuery;
import com.pawmento.pet.application.port.in.GetPetOwnerResult;
import com.pawmento.pet.application.port.in.GetPetOwnerUseCase;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PetOwnerAdapter implements LoadPetOwnerPort {

    private final GetPetOwnerUseCase getPetOwner;

    @Override 
    public Optional<Long> loadOwnerId(Long petId) {
        return getPetOwner.get(new GetPetOwnerQuery(petId)).map(GetPetOwnerResult::ownerMemberId);
    }
    
}
