package com.pawmento.diary.application.port.in;

public interface CreateDiaryEntryUseCase {
  GetDiaryEntryResult create(CreateDiaryEntryCommand command);
}
