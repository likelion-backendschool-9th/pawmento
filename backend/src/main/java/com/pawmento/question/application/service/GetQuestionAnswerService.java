package com.pawmento.question.application.service;

import com.pawmento.question.application.port.in.*;
import com.pawmento.question.application.port.out.*;
import com.pawmento.question.domain.exception.QuestionAnswerNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@RequiredArgsConstructor
@Transactional(
    readOnly = true,
    isolation = Isolation.REPEATABLE_READ,
    propagation = Propagation.REQUIRES_NEW)
public class GetQuestionAnswerService implements GetQuestionAnswerUseCase {
  private final CheckQuestionPetAccessPort access;
  private final QuestionAnswerStorePort answers;
  private final QuestionAnswerAttachments attachments;

  public QuestionAnswerResult get(GetQuestionAnswerQuery q) {
    access.currentMode(q.memberId(), q.petId());
    return attachments.one(
        answers.find(q.petId(), q.answerId()).orElseThrow(QuestionAnswerNotFoundException::new));
  }
}
