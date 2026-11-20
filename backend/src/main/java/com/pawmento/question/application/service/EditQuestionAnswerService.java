package com.pawmento.question.application.service;

import com.pawmento.question.application.port.in.*;
import com.pawmento.question.application.port.out.*;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class EditQuestionAnswerService implements EditQuestionAnswerUseCase {
  private final QuestionAnswerWriteAccess access;
  private final QuestionAnswerStorePort answers;
  private final Clock clock;
  private final QuestionAnswerAttachments attachments;

  public QuestionAnswerResult edit(EditQuestionAnswerCommand c) {
    var answer = access.requireEditable(c.memberId(), c.petId(), c.answerId(), c.version());
    boolean attachmentChanged = attachments.changes(answer, c.attachmentIds());
    if (c.content() == null && !attachmentChanged) return attachments.one(answer);
    answer.edit(c.content() == null ? answer.getContent() : c.content(), clock.instant());
    var saved = answers.save(answer);
    if (attachmentChanged) attachments.replace(c.memberId(), saved, c.attachmentIds());
    return attachments.one(saved);
  }
}
