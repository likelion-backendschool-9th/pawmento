package com.pawmento.diary.application.port.out;

public interface DeleteDiaryEntryPort {
  void delete(Long id, Long version);
}
