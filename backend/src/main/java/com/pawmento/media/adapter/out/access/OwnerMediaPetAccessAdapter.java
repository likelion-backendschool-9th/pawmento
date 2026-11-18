package com.pawmento.media.adapter.out.access;

import com.pawmento.media.application.port.out.CheckMediaPetAccessPort;
import com.pawmento.pet.application.port.in.GetPetQuery;
import com.pawmento.pet.application.port.in.GetPetUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 가족 멤버십이 구현될 때 이 어댑터를 교체한다. 현재는 실제 pet 소유자만 허용한다.
@Component
@RequiredArgsConstructor
public class OwnerMediaPetAccessAdapter implements CheckMediaPetAccessPort {
  private final GetPetUseCase pets;

  @Override
  public void requireWriteAccess(Long memberId, Long petId) {
    pets.get(new GetPetQuery(memberId, petId));
  }
}
