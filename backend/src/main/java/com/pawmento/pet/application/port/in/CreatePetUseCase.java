package com.pawmento.pet.application.port.in;

public interface CreatePetUseCase {

	PetResult create(CreatePetCommand command);

}
