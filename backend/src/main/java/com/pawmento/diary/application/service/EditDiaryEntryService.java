package com.pawmento.diary.application.service;

import com.pawmento.diary.application.port.in.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@RequiredArgsConstructor
public class EditDiaryEntryService implements EditDiaryEntryUseCase {
  private final EditDiaryEntryTransaction writer;
  private final DiaryLocationPreparation preparation;

  @Override
  @Transactional(propagation = Propagation.NEVER)
  public GetDiaryEntryResult edit(EditDiaryEntryCommand c) {
    var features = preparation.edit(c);
    return writer.edit(
        new EditDiaryEntryCommand(
            c.memberId(),
            c.petId(),
            c.diaryId(),
            c.content(),
            c.recordedDate(),
            c.version(),
            features));
  }
}
