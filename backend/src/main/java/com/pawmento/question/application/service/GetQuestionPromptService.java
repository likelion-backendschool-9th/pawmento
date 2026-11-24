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
public class GetQuestionPromptService implements GetQuestionPromptUseCase {
  private final CheckQuestionPetAccessPort access;
  private final LoadQuestionPromptHistoryPort history;
  private final QuestionAnswerStorePort answers;
  private final QuestionAnswerAttachments attachments;

  public GetQuestionPromptResult get(GetQuestionPromptQuery q) {
    var mode = access.currentMode(q.memberId(), q.petId());
    var summary =
        history
            .findSummary(q.petId(), q.promptId(), q.memberId())
            .orElseThrow(QuestionAnswerNotFoundException::new);
    var filter =
        new QuestionAnswerStorePort.Filter(
            q.petId(), null, q.promptId(), null, null, null, q.page(), q.size());
    var items = attachments.many(q.petId(), answers.list(filter));
    long count = summary.answerCount();
    return new GetQuestionPromptResult(
        QuestionPromptHistoryItem.from(summary, mode),
        new QuestionAnswersPage(
            items, q.page(), q.size(), count, count / q.size() + (count % q.size() == 0 ? 0 : 1)));
  }
}
