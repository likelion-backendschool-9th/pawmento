package com.pawmento.pet.application.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import com.pawmento.pet.application.exception.PetNotFoundException;
import com.pawmento.pet.application.port.in.*;
import com.pawmento.pet.application.port.out.LockPetPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@RequiredArgsConstructor
public class GetPetForUpdateService implements GetPetForUpdateUseCase {
	private final Clock clock;

  private final LockPetPort pets;

  @Transactional(propagation = Propagation.MANDATORY)
  public PetResult getForUpdate(GetPetQuery query) {
    var pet = pets.lock(query.petId()).orElseThrow(PetNotFoundException::new);
    pet.requireOwnedBy(query.memberId());
    return PetResult.from(pet, LocalDate.now(clock.withZone(ZoneId.of("Asia/Seoul"))));
  }
}
