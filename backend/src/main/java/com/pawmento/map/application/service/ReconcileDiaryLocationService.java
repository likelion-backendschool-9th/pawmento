package com.pawmento.map.application.service;

import com.pawmento.map.application.port.in.*;
import com.pawmento.map.application.port.out.DiaryLocationStorePort;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@RequiredArgsConstructor
public class ReconcileDiaryLocationService implements ReconcileDiaryLocationUseCase {
  private final DiaryLocationStorePort store;

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public DiaryLocationApplyResult reconcile(ReconcileDiaryLocationCommand c) {
    var old = store.lock(c.diaryId()).orElse(null);
    if (!Objects.equals(old, c.expected())) return DiaryLocationApplyResult.STALE;
    if (c.location() == null) {
      if (old == null) return DiaryLocationApplyResult.DUPLICATE;
      store.remove(c.diaryId());
    } else store.write(c.diaryId(), c.petId(), c.createdBy(), c.revision(), c.location());
    return DiaryLocationApplyResult.APPLIED;
  }
}
