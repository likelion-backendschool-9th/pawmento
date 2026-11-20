package com.pawmento.pet.adapter.out.persistence;

import com.pawmento.pet.application.port.out.LockPetPort;
import com.pawmento.pet.domain.Pet;
import jakarta.persistence.*;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class LockPetPersistenceAdapter implements LockPetPort {
  private final EntityManager em;

  public Optional<Pet> lock(Long id) {
    var entity = em.find(PetJpaEntity.class, id, LockModeType.PESSIMISTIC_WRITE);
    if (entity == null) return Optional.empty();
    em.refresh(entity, LockModeType.PESSIMISTIC_WRITE);
    return Optional.of(entity.toDomain());
  }
}
