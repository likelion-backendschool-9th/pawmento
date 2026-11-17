package com.pawmento.pet.application.port.out;

import com.pawmento.pet.domain.Pet;

public interface SavePetPort {

	Long save(Pet pet);

}
