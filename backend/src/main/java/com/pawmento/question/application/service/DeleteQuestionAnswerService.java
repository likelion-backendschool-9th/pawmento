package com.pawmento.question.application.service;

import com.pawmento.question.application.port.in.*;
import com.pawmento.question.application.port.out.*;
import com.pawmento.question.domain.exception.QuestionAnswerNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class DeleteQuestionAnswerService implements DeleteQuestionAnswerUseCase {
  private final CheckQuestionPetWriteAccessPort access;
  private final QuestionAnswerStorePort answers;
  private final QuestionAnswerAttachments attachments;

  public void delete(DeleteQuestionAnswerCommand c) {
    var mode = access.currentModeForWrite(c.memberId(), c.petId());
    var answer =
        answers.lock(c.petId(), c.answerId()).orElseThrow(QuestionAnswerNotFoundException::new);
    answer.requireEditable(c.memberId(), mode, c.version());
    attachments.replace(c.memberId(), answer, java.util.List.of());
    answers.delete(answer);
  }
}
