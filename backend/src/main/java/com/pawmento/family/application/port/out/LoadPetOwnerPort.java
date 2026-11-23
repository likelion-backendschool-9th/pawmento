package com.pawmento.family.application.port.out;

import java.util.Optional;

public interface LoadPetOwnerPort {
    Optional<Long> loadOwnerId(Long petId);
}
