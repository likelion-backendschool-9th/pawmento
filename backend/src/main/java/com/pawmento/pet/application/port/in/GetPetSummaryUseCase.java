package com.pawmento.pet.application.port.in;

import java.util.Optional;

public interface GetPetSummaryUseCase {
    Optional<PetSummaryResult> get(GetPetSummaryQuery query);
}
