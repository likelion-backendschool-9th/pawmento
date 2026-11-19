package com.pawmento.diary.application.port.in;

public interface ListDiaryEntriesUseCase {
  ListDiaryEntriesResult list(ListDiaryEntriesQuery query);
}
