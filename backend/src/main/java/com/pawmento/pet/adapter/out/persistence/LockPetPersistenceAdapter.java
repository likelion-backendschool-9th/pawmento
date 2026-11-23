package com.pawmento.pet.adapter.out.persistence;

import com.pawmento.pet.application.port.out.LockPetPort;
import com.pawmento.pet.domain.Pet;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class LockPetPersistenceAdapter implements LockPetPort {
  private final EntityManager em;

  public Optional<Pet> lock(Long id) {
    // 첫 조회에서 관리 중인 엔티티 버전과 관계없이 현재 DB 행을 잠근다.
    var lockedIds = em.createNativeQuery("select id from pets where id = :id for update")
        .setParameter("id", id)
        .getResultList();
    if (lockedIds.isEmpty()) return Optional.empty();
    var entity = em.find(PetJpaEntity.class, id);
    em.refresh(entity);
    return Optional.of(entity.toDomain());
  }
}
