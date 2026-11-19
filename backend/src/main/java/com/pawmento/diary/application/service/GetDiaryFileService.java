package com.pawmento.diary.application.service;

import com.pawmento.diary.application.exception.DiaryEntryNotFoundException;
import com.pawmento.diary.application.port.in.*;
import com.pawmento.diary.application.port.out.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetDiaryFileService implements GetDiaryFileUseCase {
  private final CheckDiaryPetAccessPort access;
  private final LoadDiaryEntryPort load;
  private final DiaryAttachmentsPort attachments;
  private final DiaryMediaPort media;

  @Transactional(readOnly = true)
  public GetDiaryFileResult get(GetDiaryFileQuery q) {
    access.requireAccess(q.memberId(), q.petId());
    DiaryEntryAccess.load(load, q.petId(), q.diaryId());
    if (!attachments.find(q.diaryId()).contains(q.assetId()))
      throw new DiaryEntryNotFoundException();
    return media.file(q.petId(), q.assetId(), q.thumbnail());
  }
}
