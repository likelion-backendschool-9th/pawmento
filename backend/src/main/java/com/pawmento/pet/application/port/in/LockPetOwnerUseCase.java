package com.pawmento.pet.application.port.in;

import java.util.Optional;

public interface LockPetOwnerUseCase {
    
    Optional<GetPetOwnerResult> lock(LockPetOwnerQuery query);
}
