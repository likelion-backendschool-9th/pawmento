package com.pawmento.map.application.service;

import com.pawmento.map.application.port.in.*;
import com.pawmento.map.application.port.out.DiaryLocationStorePort;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetDiaryLocationService implements GetDiaryLocationUseCase {
  private final DiaryLocationStorePort store;

  @Override
  @Transactional(readOnly = true)
  public Optional<DiaryLocationState> get(Long id) {
    if (id == null || id <= 0) throw new IllegalArgumentException("일기 ID가 필요합니다.");
    return store.read(id);
  }
}
