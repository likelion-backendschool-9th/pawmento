package com.pawmento.question.application.service;

import com.pawmento.question.application.port.in.*;
import com.pawmento.question.application.port.out.*;
import com.pawmento.question.domain.exception.QuestionAnswerNotFoundException;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class EditQuestionAnswerService implements EditQuestionAnswerUseCase {
  private final CheckQuestionPetWriteAccessPort access;
  private final QuestionAnswerStorePort answers;
  private final Clock clock;
  private final QuestionAnswerAttachments attachments;

  public QuestionAnswerResult edit(EditQuestionAnswerCommand c) {
    var mode = access.currentModeForWrite(c.memberId(), c.petId());
    var answer =
        answers.lock(c.petId(), c.answerId()).orElseThrow(QuestionAnswerNotFoundException::new);
    answer.requireEditable(c.memberId(), mode, c.version());
    boolean attachmentChanged = attachments.changes(answer, c.attachmentIds());
    if (c.content() == null && !attachmentChanged) return attachments.one(answer);
    answer.edit(c.content() == null ? answer.getContent() : c.content(), clock.instant());
    var saved = answers.save(answer);
    if (attachmentChanged) attachments.replace(c.memberId(), saved, c.attachmentIds());
    return attachments.one(saved);
  }
}
