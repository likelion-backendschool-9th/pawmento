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

  @Override
  @Transactional
  public void delete(DeleteDiaryEntryCommand c) {
    access.requireAccess(c.memberId(), c.petId());
    var entry = DiaryEntryAccess.load(load, c.petId(), c.recordId());
    entry.requireAuthor(c.memberId());
    DiaryEntryAccess.version(entry, c.version());
    entry = DiaryEntryAccess.locked(load, c.petId(), c.recordId());
    DiaryEntryAccess.version(entry, c.version());
    var previous = attachments.find(entry.getId());
    media.lock(previous);
    deletion.delete(entry.getId(), c.version());
    media.change(c.memberId(), c.petId(), previous, java.util.List.of());
  }
}
