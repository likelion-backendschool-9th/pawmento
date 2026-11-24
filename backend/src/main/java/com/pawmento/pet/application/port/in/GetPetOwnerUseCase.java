package com.pawmento.pet.application.port.in;

import java.util.Optional;

public interface GetPetOwnerUseCase {
    Optional<GetPetOwnerResult> get(GetPetOwnerQuery query);
}
