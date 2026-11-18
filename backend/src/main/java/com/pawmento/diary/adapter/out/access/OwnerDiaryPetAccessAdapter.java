package com.pawmento.diary.adapter.out.access;

import com.pawmento.diary.application.port.out.CheckDiaryPetAccessPort;
import com.pawmento.pet.application.port.in.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 가족 접근 계약이 연결되기 전에는 실제 pet 소유자만 허용한다.
@Component
@RequiredArgsConstructor
public class OwnerDiaryPetAccessAdapter implements CheckDiaryPetAccessPort {
  private final GetPetUseCase pets;

  public void requireAccess(Long memberId, Long petId) {
    pets.get(new GetPetQuery(memberId, petId));
  }
}
