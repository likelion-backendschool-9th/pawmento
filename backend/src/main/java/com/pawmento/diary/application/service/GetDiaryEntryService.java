package com.pawmento.diary.application.service;

import com.pawmento.diary.application.port.in.*;
import com.pawmento.diary.application.port.out.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetDiaryEntryService implements GetDiaryEntryUseCase {
  private final CheckDiaryPetAccessPort access;
  private final LoadDiaryEntryPort load;
  private final DiaryResults results;

  @Override
  @Transactional(readOnly = true)
  public GetDiaryEntryResult get(GetDiaryEntryQuery c) {
    access.requireAccess(c.memberId(), c.petId());
    return results.one(DiaryEntryAccess.load(load, c.petId(), c.recordId()));
  }
}
