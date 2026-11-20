package com.pawmento.question.application.service;

import com.pawmento.question.application.port.in.HasQuestionAttachmentUseCase;
import com.pawmento.question.application.port.out.QuestionAttachmentsPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class HasQuestionAttachmentService implements HasQuestionAttachmentUseCase {
  private final QuestionAttachmentsPort links;

  @Transactional(readOnly = true)
  public boolean exists(Long id) {
    return links.existsByAssetId(id);
  }
}
