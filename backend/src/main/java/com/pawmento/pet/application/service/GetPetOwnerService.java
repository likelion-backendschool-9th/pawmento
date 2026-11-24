package com.pawmento.pet.application.service;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;


import com.pawmento.pet.application.port.in.GetPetOwnerQuery;
import com.pawmento.pet.application.port.in.GetPetOwnerResult;
import com.pawmento.pet.application.port.in.GetPetOwnerUseCase;
import com.pawmento.pet.application.port.in.LockPetOwnerQuery;
import com.pawmento.pet.application.port.in.LockPetOwnerUseCase;
import com.pawmento.pet.application.port.out.LoadPetPort;
import com.pawmento.pet.application.port.out.LockPetPort;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GetPetOwnerService implements GetPetOwnerUseCase, LockPetOwnerUseCase {

    private final LoadPetPort loadPetPort;

    private final LockPetPort lockPetPort;

    @Override
    @Transactional(readOnly = true)
    public Optional<GetPetOwnerResult> get(GetPetOwnerQuery query) {
        return loadPetPort.findById(query.petId()).map(pet -> new GetPetOwnerResult(pet.getOwnerMemberId(), pet.getMode().name()));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<GetPetOwnerResult> lock(LockPetOwnerQuery query) {
        return lockPetPort.lock(query.petId()).map(pet -> new GetPetOwnerResult(pet.getOwnerMemberId(), pet.getMode().name()));
    }

}
