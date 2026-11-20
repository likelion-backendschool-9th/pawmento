package com.pawmento.diary.adapter.out.persistence;

import com.pawmento.diary.application.exception.DiaryPersistenceUnavailableException;
import com.pawmento.diary.application.port.out.DiaryAttachmentsPort;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

@Component
@RequiredArgsConstructor
public class DiaryAttachmentsAdapter implements DiaryAttachmentsPort {
  private final DiaryAttachmentJpaRepository rows;

  public Map<Long, List<Long>> findAll(List<Long> ids) {
    if (ids.isEmpty()) return Map.of();
    return run(
        () -> {
          Map<Long, List<Long>> result = new HashMap<>();
          for (var row : rows.findForParents(ids))
            result
                .computeIfAbsent(row.getId().getDiaryId(), k -> new ArrayList<>())
                .add(row.getId().getAssetId());
          return result;
        });
  }

  @Transactional
  public void replace(Long id, List<Long> assets) {
    run(
        () -> {
          // Remove managed links and flush before inserting reused composite IDs/order slots.
          // Bulk delete would leave previously loaded link entities stale in this transaction.
          rows.deleteById_DiaryId(id);
          rows.flush();
          rows.saveAllAndFlush(
              IntStream.range(0, assets.size())
                  .mapToObj(i -> DiaryAttachmentJpaEntity.from(id, assets.get(i), i))
                  .toList());
          return null;
        });
  }

  public boolean existsByAssetId(Long id) {
    return run(() -> rows.existsById_AssetId(id));
  }

  private static <T> T run(Supplier<T> action) {
    try {
      return action.get();
    } catch (DataAccessResourceFailureException | TransientDataAccessResourceException failure) {
      throw new DiaryPersistenceUnavailableException(failure);
    }
  }
}
