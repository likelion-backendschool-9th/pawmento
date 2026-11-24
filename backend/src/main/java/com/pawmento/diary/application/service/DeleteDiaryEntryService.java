package com.pawmento.diary.application.service;

import com.pawmento.diary.application.port.in.*;
import com.pawmento.diary.application.port.out.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeleteDiaryEntryService implements DeleteDiaryEntryUseCase {
  private final CheckDiaryPetAccessPort access;
  private final DiaryAttachmentsPort attachments;
  private final DiaryMediaPort media;
  private final LoadDiaryEntryPort load;
  private final DeleteDiaryEntryPort deletion;
  private final DiaryLocationManager location;

  @Override
  @Transactional
  public void delete(DeleteDiaryEntryCommand c) {
    access.requireAccess(c.memberId(), c.petId());
    var entry = DiaryEntryAccess.load(load, c.petId(), c.diaryId());
    entry.requireAuthor(c.memberId());
    DiaryEntryAccess.version(entry, c.version());
    access.requireLockedWriteAccess(c.memberId(), c.petId());
    entry = DiaryEntryAccess.locked(load, c.petId(), c.diaryId());
    DiaryEntryAccess.version(entry, c.version());
    DiaryLocationManager.next(entry.getVersion());
    var observed = location.observe(entry);
    var previous = attachments.find(entry.getId());
    media.lock(c.memberId(), c.petId(), previous);
    deletion.delete(entry.getId(), c.version());
    media.change(c.memberId(), c.petId(), previous, java.util.List.of());
    location.deleted(entry, observed);
  }
}
