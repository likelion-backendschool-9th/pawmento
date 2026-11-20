package com.pawmento.pet.application.port.in;

public interface GetPetForUpdateUseCase {
  PetResult getForUpdate(GetPetQuery query);
}
