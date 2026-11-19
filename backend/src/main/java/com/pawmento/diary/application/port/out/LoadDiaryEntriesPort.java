package com.pawmento.diary.application.port.out;

import com.pawmento.diary.domain.*;
import java.util.List;

public interface LoadDiaryEntriesPort {
  List<DiaryEntry> entries(Long petId, DiaryListFilter filter);
}
