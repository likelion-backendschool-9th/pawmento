package com.pawmento.diary.application.service;

import com.pawmento.diary.application.port.in.HasDiaryAttachmentUseCase;
import com.pawmento.diary.application.port.out.DiaryAttachmentsPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class HasDiaryAttachmentService implements HasDiaryAttachmentUseCase {
  private final DiaryAttachmentsPort attachments;

  @Transactional(readOnly = true)
  public boolean exists(Long id) {
    return attachments.existsByAssetId(id);
  }
}
