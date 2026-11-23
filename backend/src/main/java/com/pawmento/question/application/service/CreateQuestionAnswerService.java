package com.pawmento.question.application.service;

import com.pawmento.question.application.port.in.*;
import com.pawmento.question.application.port.out.*;
import com.pawmento.question.domain.*;
import com.pawmento.question.domain.exception.*;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class CreateQuestionAnswerService implements CreateQuestionAnswerUseCase {
  private final CheckQuestionPetWriteAccessPort access;
  private final LoadQuestionAuthorNamePort authors;
  private final QuestionAnswerStorePort answers;
  private final QuestionProgressStorePort progresses;
  private final QuestionPromptCompletionPort prompts;
  private final Clock clock;
  private final QuestionAnswerAttachments attachments;

  public CreateQuestionAnswerResult create(CreateQuestionAnswerCommand c) {
    var mode = access.currentModeForWrite(c.memberId(), c.petId());
    // Lock common progress before loading the prompt: a waiting writer must see completion
    // committed by its predecessor.
    var progress = progresses.lock(c.petId(), mode);
    var prompt =
        prompts.find(c.petId(), c.promptId()).orElseThrow(QuestionAnswerNotFoundException::new);
    if (prompt.getMode() != mode || answers.exists(c.promptId(), c.memberId()))
      throw new QuestionAnswerConflictException();
    boolean completedNow = prompt.getCompletedAt() == null;
    if (completedNow) progress.requireCurrent(prompt);
    var now = clock.instant();
    var saved =
        answers.save(
            QuestionAnswer.create(
                prompt, c.memberId(), authors.loadName(c.memberId()), c.content(), now));
    attachments.replace(c.memberId(), saved, c.attachmentIds());
    if (completedNow) {
      prompt.complete(now);
      progress.completeCurrent(prompt);
      prompts.complete(prompt);
      progresses.saveProgress(progress);
    }
    return new CreateQuestionAnswerResult(
        attachments.one(saved), completedNow, progress.getCurrentPromptId() == null);
  }
}
