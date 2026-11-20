package com.pawmento.map.application.service;

import com.pawmento.map.application.exception.DiaryLocationContractViolation;
import com.pawmento.map.application.port.in.*;
import com.pawmento.map.application.port.out.DiaryLocationStorePort;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@RequiredArgsConstructor
public class ApplyDiaryLocationService implements ApplyDiaryLocationUseCase {
  private final DiaryLocationStorePort store;

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public DiaryLocationApplyResult apply(ApplyDiaryLocationCommand c) {
    var old = store.lock(c.diaryId()).orElse(null);
    if (old != null) {
      if (!Objects.equals(old.petId(), c.petId())
          || !Objects.equals(old.createdBy(), c.createdBy()))
        throw new DiaryLocationContractViolation();
      if (c.revision() < old.revision()) return DiaryLocationApplyResult.STALE;
      if (c.revision() == old.revision()) {
        if (c.location() != null
            && "MANUAL".equals(old.source())
            && old.sourceAssetId() == null
            && Objects.equals(old.value(), c.location())) return DiaryLocationApplyResult.DUPLICATE;
        throw new DiaryLocationContractViolation();
      }
    }
    if (c.location() == null) {
      if (old == null) return DiaryLocationApplyResult.DUPLICATE;
      store.remove(c.diaryId());
    } else store.write(c.diaryId(), c.petId(), c.createdBy(), c.revision(), c.location());
    return DiaryLocationApplyResult.APPLIED;
  }
}
