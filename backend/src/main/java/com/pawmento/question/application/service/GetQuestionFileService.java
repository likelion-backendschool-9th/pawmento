package com.pawmento.question.application.service;

import com.pawmento.question.application.port.in.*;
import com.pawmento.question.application.port.out.*;
import com.pawmento.question.domain.exception.QuestionAnswerNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetQuestionFileService implements GetQuestionFileUseCase {
  private final CheckQuestionPetAccessPort access;
  private final QuestionAnswerStorePort answers;
  private final QuestionAttachmentsPort links;
  private final QuestionMediaPort media;

  @Transactional(readOnly = true)
  public GetQuestionFileResult get(GetQuestionFileQuery q) {
    access.currentMode(q.memberId(), q.petId());
    answers.find(q.petId(), q.answerId()).orElseThrow(QuestionAnswerNotFoundException::new);
    if (!links.find(q.answerId()).contains(q.assetId()))
      throw new QuestionAnswerNotFoundException();
    return media.file(q.petId(), q.assetId(), q.thumbnail());
  }
}
