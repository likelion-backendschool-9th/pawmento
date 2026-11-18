package com.pawmento.pet.adapter.out.records;

import com.pawmento.diary.application.port.in.*;
import com.pawmento.pet.application.port.out.HasPetRecordsPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DiaryPetRecordsAdapter implements HasPetRecordsPort {
  private final HasDiaryEntriesUseCase diaries;

  @Override
  public boolean existsByPetId(Long petId) {
    return diaries.exists(new HasDiaryEntriesQuery(petId));
  }
}
