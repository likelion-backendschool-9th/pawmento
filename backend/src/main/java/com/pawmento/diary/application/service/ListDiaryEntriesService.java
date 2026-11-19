package com.pawmento.diary.application.service;

import com.pawmento.diary.application.port.in.*;
import com.pawmento.diary.application.port.out.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@RequiredArgsConstructor
public class ListDiaryEntriesService implements ListDiaryEntriesUseCase {
  private final CheckDiaryPetAccessPort access;
  private final LoadDiaryEntriesPort entries;
  private final CountDiaryEntriesPort counts;
  private final DiaryResults results;

  @Override
  @Transactional(
      readOnly = true,
      isolation = Isolation.REPEATABLE_READ,
      propagation = Propagation.REQUIRES_NEW)
  public ListDiaryEntriesResult list(ListDiaryEntriesQuery query) {
    access.requireAccess(query.memberId(), query.petId());
    var filter = query.filter();
    var result = entries.entries(query.petId(), filter);
    long total = counts.count(query.petId(), filter);
    long pages = total / filter.size() + (total % filter.size() == 0 ? 0 : 1);
    return new ListDiaryEntriesResult(
        results.many(query.petId(), result), filter.page(), filter.size(), total, pages);
  }
}
