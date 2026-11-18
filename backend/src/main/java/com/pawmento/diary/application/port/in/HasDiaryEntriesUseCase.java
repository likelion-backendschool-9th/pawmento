package com.pawmento.diary.application.port.in;

public interface HasDiaryEntriesUseCase {
  boolean exists(HasDiaryEntriesQuery query);
}
