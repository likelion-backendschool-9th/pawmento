package com.pawmento.diary.application.port.out;

public interface SaveDiaryEntryPort {
  com.pawmento.diary.domain.DiaryEntry save(com.pawmento.diary.domain.DiaryEntry entry);
}
