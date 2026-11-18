package com.pawmento.diary.application.service;

import com.pawmento.diary.application.port.in.*;
import com.pawmento.diary.application.port.out.ExistsDiaryEntriesPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class HasDiaryEntriesService implements HasDiaryEntriesUseCase {
  private final ExistsDiaryEntriesPort entries;

  @Override
  @Transactional(readOnly = true)
  public boolean exists(HasDiaryEntriesQuery query) {
    return entries.existsByPetId(query.petId());
  }
}
