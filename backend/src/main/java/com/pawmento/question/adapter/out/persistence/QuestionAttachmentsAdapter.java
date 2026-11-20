package com.pawmento.question.adapter.out.persistence;

import com.pawmento.question.application.port.out.QuestionAttachmentsPort;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

@Component
@RequiredArgsConstructor
public class QuestionAttachmentsAdapter implements QuestionAttachmentsPort {
  private final QuestionAttachmentJpaRepository rows;

  public Map<Long, List<Long>> findAll(List<Long> ids) {
    if (ids.isEmpty()) return Map.of();
    return run(
        () -> {
          Map<Long, List<Long>> result = new HashMap<>();
          for (var row : rows.findForParents(ids))
            result
                .computeIfAbsent(row.getId().getAnswerId(), k -> new ArrayList<>())
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
          rows.deleteById_AnswerId(id);
          rows.flush();
          rows.saveAllAndFlush(
              IntStream.range(0, assets.size())
                  .mapToObj(i -> QuestionAttachmentJpaEntity.from(id, assets.get(i), i))
                  .toList());
          return null;
        });
  }

  public boolean existsByAssetId(Long id) {
    return run(() -> rows.existsById_AssetId(id));
  }

  private static <T> T run(Supplier<T> action) {
    return QuestionPersistenceOperations.execute(action);
  }
}
