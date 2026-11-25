package com.pawmento.family.application.port.out;

import java.util.Optional;

public interface LoadPetSummaryPort {
    Optional<PetSummary> loadSummary(Long petId);
}
