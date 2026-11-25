package com.pawmento.pet.application.service;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pawmento.pet.application.port.in.GetPetSummaryQuery;
import com.pawmento.pet.application.port.in.GetPetSummaryUseCase;
import com.pawmento.pet.application.port.in.PetSummaryResult;
import com.pawmento.pet.application.port.out.LoadPetPort;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GetPetSummaryService implements GetPetSummaryUseCase {

    private final LoadPetPort loadPetPort;

    @Override
    @Transactional(readOnly = true)
    public Optional<PetSummaryResult> get(GetPetSummaryQuery query) {
        return loadPetPort.findById(query.petId())
                .map(pet -> new PetSummaryResult(pet.getId(), pet.getName(), pet.getTag()));
    }
}
