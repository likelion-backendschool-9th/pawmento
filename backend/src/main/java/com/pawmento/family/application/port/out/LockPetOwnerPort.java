package com.pawmento.family.application.port.out;

import java.util.Optional;

public interface LockPetOwnerPort {

    Optional<Long> lockOwnerId(Long petId);
}
