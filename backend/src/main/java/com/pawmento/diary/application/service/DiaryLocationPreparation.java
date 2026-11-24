package com.pawmento.diary.application.service;

import com.pawmento.diary.application.port.in.*;
import com.pawmento.diary.application.port.out.*;
import com.pawmento.diary.domain.DiaryEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DiaryLocationPreparation {
  private final CheckDiaryPetAccessPort access;
  private final LoadDiaryEntryPort load;
  private final DiaryAttachmentsPort attachments;
  private final DiaryMapPort map;
  private final java.time.Clock clock;
  private final DiaryMediaPort media;

  DiaryWriteFeatures create(CreateDiaryEntryCommand c) {
    DiaryWritePolicy.requireSupported(c.features());
    access.requireAccess(c.memberId(), c.petId());
    DiaryEntry.validateDraft(
        c.petId(),
        c.memberId(),
        c.content(),
        c.recordedDate(),
        clock.instant(),
        c.features().attachmentIds() != null && !c.features().attachmentIds().isEmpty());
    media.validate(
        c.memberId(),
        c.petId(),
        java.util.List.of(),
        c.features().attachmentIds() == null ? java.util.List.of() : c.features().attachmentIds());
    return resolve(c.features());
  }

  DiaryWriteFeatures edit(EditDiaryEntryCommand c) {
    DiaryWritePolicy.requireSupported(c.features());
    access.requireAccess(c.memberId(), c.petId());
    var entry = DiaryEntryAccess.load(load, c.petId(), c.diaryId());
    entry.requireAuthor(c.memberId());
    DiaryEntryAccess.version(entry, c.version());
    var ids =
        c.features().attachmentIds() == null
            ? attachments.find(entry.getId())
            : c.features().attachmentIds();
    entry.edit(c.memberId(), c.content(), c.recordedDate(), clock.instant(), !ids.isEmpty());
    media.validate(c.memberId(), c.petId(), attachments.find(entry.getId()), ids);
    return resolve(c.features());
  }

  private DiaryWriteFeatures resolve(DiaryWriteFeatures f) {
    var change = f.location();
    if (change.kind() != DiaryLocationChange.Kind.MANUAL || change.intent().placeName() != null)
      return f;
    var v = change.intent();
    var name = map.resolveAddress(v.latitude(), v.longitude()).orElse(null);
    return new DiaryWriteFeatures(
        f.attachmentIds(), DiaryLocationChange.manual(v.latitude(), v.longitude(), name));
  }
}
