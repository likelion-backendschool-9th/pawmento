package com.pawmento.diary.application.port.out;

public interface LoadDiaryEntryPort {
  java.util.Optional<com.pawmento.diary.domain.DiaryEntry> findById(Long id);
}
