package com.pawmento.map.application.port.in;

import java.util.Optional;

public interface GetDiaryLocationUseCase {
  Optional<DiaryLocationState> get(Long diaryId);
}
