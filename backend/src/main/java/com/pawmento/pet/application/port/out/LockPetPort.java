package com.pawmento.pet.application.port.out;

import com.pawmento.pet.domain.Pet;
import java.util.Optional;

public interface LockPetPort {
  Optional<Pet> lock(Long petId);
}
