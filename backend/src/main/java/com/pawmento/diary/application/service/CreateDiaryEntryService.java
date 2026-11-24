package com.pawmento.diary.application.service;

import com.pawmento.diary.application.port.in.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@RequiredArgsConstructor
public class CreateDiaryEntryService implements CreateDiaryEntryUseCase {
  private final CreateDiaryEntryTransaction writer;
  private final DiaryLocationPreparation preparation;

  @Override
  @Transactional(propagation = Propagation.NEVER)
  public GetDiaryEntryResult create(CreateDiaryEntryCommand c) {
    var features = preparation.create(c);
    return writer.create(
        new CreateDiaryEntryCommand(
            c.memberId(), c.petId(), c.content(), c.recordedDate(), features, c.title()));
  }
}
