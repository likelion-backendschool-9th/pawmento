package com.pawmento.map.application.port.out;

import com.pawmento.map.application.port.in.*;
import java.util.Optional;

public interface DiaryLocationStorePort {
  Optional<DiaryLocationState> read(Long diaryId);

  Optional<DiaryLocationState> lock(Long diaryId);

  void write(Long diaryId, Long petId, Long createdBy, long revision, DiaryLocationValue location);

  void remove(Long diaryId);
}
