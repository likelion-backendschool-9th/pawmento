package com.pawmento.diary.application.service;

import com.pawmento.diary.application.port.in.*;
import com.pawmento.diary.application.port.out.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EditDiaryEntryTransaction {
  private final CheckDiaryPetAccessPort access;
  private final DiaryAttachmentsPort attachments;
  private final DiaryMediaPort media;
  private final DiaryResults results;
  private final LoadDiaryEntryPort load;
  private final SaveDiaryEntryPort save;
  private final java.time.Clock clock;
  private final DiaryLocationManager location;

  @Transactional
  public GetDiaryEntryResult edit(EditDiaryEntryCommand c) {
    DiaryWritePolicy.requireSupported(c.features());
    access.requireAccess(c.memberId(), c.petId());
    var entry = DiaryEntryAccess.load(load, c.petId(), c.diaryId());
    entry.requireAuthor(c.memberId());
    DiaryEntryAccess.version(entry, c.version());
    access.requireLockedWriteAccess(c.memberId(), c.petId());
    entry = DiaryEntryAccess.locked(load, c.petId(), c.diaryId());
    DiaryEntryAccess.version(entry, c.version());
    DiaryLocationManager.next(entry.getVersion());
    var observed = location.observe(entry);
    boolean explicit = c.features().location().kind() != DiaryLocationChange.Kind.KEEP;
    if (!explicit) location.requireCoherent(entry, observed);
    if (c.content() == null
        && c.recordedDate() == null
        && c.features().attachmentIds() == null
        && !explicit) throw new com.pawmento.diary.domain.exception.InvalidDiaryEntryException();
    // 조건부 부모 갱신이 경합에서 지면 첨부 변경도 진행하지 않는다.
    var previous = attachments.find(entry.getId());
    var next = c.features().attachmentIds() == null ? previous : c.features().attachmentIds();
    entry.edit(c.memberId(), c.content(), c.recordedDate(), clock.instant(), !next.isEmpty());
    if (explicit)
      entry.changeLocation(c.memberId(), c.features().location().intent(), clock.instant());
    var saved = save.save(entry);
    var locks = new java.util.TreeSet<Long>(previous);
    locks.addAll(next);
    media.lock(c.memberId(), c.petId(), java.util.List.copyOf(locks));
    attachments.replace(entry.getId(), next);
    media.change(c.memberId(), c.petId(), previous, next);
    location.synchronize(saved, observed, explicit);
    return results.one(saved);
  }
}
