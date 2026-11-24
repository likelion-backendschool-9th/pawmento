package com.pawmento.diary.application.service;

import com.pawmento.diary.application.port.in.*;
import com.pawmento.diary.application.port.out.*;
import com.pawmento.diary.domain.DiaryEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CreateDiaryEntryTransaction {
  private final CheckDiaryPetAccessPort access;
  private final DiaryAttachmentsPort attachments;
  private final DiaryMediaPort media;
  private final DiaryResults results;
  private final SaveDiaryEntryPort save;
  private final java.time.Clock clock;
  private final DiaryLocationManager location;

  @Transactional
  public GetDiaryEntryResult create(CreateDiaryEntryCommand c) {
    DiaryWritePolicy.requireSupported(c.features());
    access.requireLockedWriteAccess(c.memberId(), c.petId());
    var ids =
        c.features().attachmentIds() == null
            ? java.util.List.<Long>of()
            : c.features().attachmentIds();
    var original =
        DiaryEntry.create(
            c.petId(),
            c.memberId(),
            c.content(),
            c.recordedDate(),
            clock.instant(),
            !ids.isEmpty());
    if (c.features().location().intent() != null)
      original.changeLocation(c.memberId(), c.features().location().intent(), clock.instant());
    var entry = save.save(original);
    media.lock(c.memberId(), c.petId(), ids);
    attachments.replace(entry.getId(), ids);
    media.change(c.memberId(), c.petId(), java.util.List.of(), ids);
    location.created(entry);
    return results.one(entry);
  }
}
