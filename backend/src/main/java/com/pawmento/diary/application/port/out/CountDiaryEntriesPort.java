package com.pawmento.diary.application.port.out;

import com.pawmento.diary.domain.DiaryListFilter;

public interface CountDiaryEntriesPort {
  long count(Long petId, DiaryListFilter filter);
}
