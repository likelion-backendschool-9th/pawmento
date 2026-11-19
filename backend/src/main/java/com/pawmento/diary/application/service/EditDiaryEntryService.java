package com.pawmento.diary.application.service;

import com.pawmento.diary.application.port.in.*;
import com.pawmento.diary.application.port.out.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EditDiaryEntryService implements EditDiaryEntryUseCase {
  private final CheckDiaryPetAccessPort access;
  private final DiaryAttachmentsPort attachments;
  private final DiaryMediaPort media;
  private final DiaryResults results;
  private final LoadDiaryEntryPort load;
  private final SaveDiaryEntryPort save;
  private final java.time.Clock clock;

  @Override
  @Transactional
  public GetDiaryEntryResult edit(EditDiaryEntryCommand c) {
    DiaryWritePolicy.requireSupported(c.features());
    access.requireAccess(c.memberId(), c.petId());
    var entry = DiaryEntryAccess.load(load, c.petId(), c.recordId());
    entry.requireAuthor(c.memberId());
    DiaryEntryAccess.version(entry, c.version());
    entry = DiaryEntryAccess.locked(load, c.petId(), c.recordId());
    DiaryEntryAccess.version(entry, c.version());
    if (c.content() == null && c.recordedDate() == null && c.features().attachmentIds() == null)
      throw new com.pawmento.diary.domain.exception.InvalidDiaryEntryException();
    // 조건부 부모 갱신이 경합에서 지면 첨부 변경도 진행하지 않는다.
    var previous = attachments.find(entry.getId());
    var next = c.features().attachmentIds() == null ? previous : c.features().attachmentIds();
    entry.edit(c.memberId(), c.content(), c.recordedDate(), clock.instant(), !next.isEmpty());
    var saved = save.save(entry);
    var locks = new java.util.TreeSet<Long>(previous);
    locks.addAll(next);
    media.lock(java.util.List.copyOf(locks));
    attachments.replace(entry.getId(), next);
    media.change(c.memberId(), c.petId(), previous, next);
    return results.one(saved);
  }
}
