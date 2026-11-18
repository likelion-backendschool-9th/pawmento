package com.pawmento.diary.application.port.in;

public interface GetDiaryEntryUseCase {
  GetDiaryEntryResult get(GetDiaryEntryQuery command);
}
