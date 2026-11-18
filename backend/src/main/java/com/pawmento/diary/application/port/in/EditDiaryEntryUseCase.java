package com.pawmento.diary.application.port.in;

public interface EditDiaryEntryUseCase {
  GetDiaryEntryResult edit(EditDiaryEntryCommand command);
}
