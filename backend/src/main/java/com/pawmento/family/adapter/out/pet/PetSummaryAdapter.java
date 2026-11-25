package com.pawmento.family.adapter.out.pet;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.pawmento.family.application.port.out.LoadPetSummaryPort;
import com.pawmento.family.application.port.out.PetSummary;
import com.pawmento.pet.application.port.in.GetPetSummaryQuery;
import com.pawmento.pet.application.port.in.GetPetSummaryUseCase;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
class PetSummaryAdapter implements LoadPetSummaryPort {

    private final GetPetSummaryUseCase getPetSummary;

    @Override
    public Optional<PetSummary> loadSummary(Long petId) {
        return getPetSummary.get(new GetPetSummaryQuery(petId))
                .map(pet -> new PetSummary(pet.petId(), pet.name(), pet.tag()));
    }
}
