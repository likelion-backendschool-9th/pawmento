package com.pawmento.question.application.service;

import com.pawmento.question.application.port.in.*;
import com.pawmento.question.application.port.out.*;
import com.pawmento.question.domain.exception.QuestionAnswerNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QuestionAnswerReadService
    implements GetQuestionAnswerUseCase, ListQuestionAnswersUseCase, HasQuestionAnswersUseCase {
  private final CheckQuestionPetAccessPort access;
  private final QuestionAnswerStorePort answers;
  private final QuestionAnswerAttachments attachments;

  @Transactional(
      readOnly = true,
      isolation = Isolation.REPEATABLE_READ,
      propagation = Propagation.REQUIRES_NEW)
  public QuestionAnswerResult get(GetQuestionAnswerQuery q) {
    access.currentMode(q.memberId(), q.petId());
    return attachments.one(
        answers.find(q.petId(), q.answerId()).orElseThrow(QuestionAnswerNotFoundException::new));
  }

  @Transactional(
      readOnly = true,
      isolation = Isolation.REPEATABLE_READ,
      propagation = Propagation.REQUIRES_NEW)
  public QuestionAnswersPage list(ListQuestionAnswersQuery q) {
    access.currentMode(q.memberId(), q.petId());
    var filter =
        new QuestionAnswerStorePort.Filter(
            q.petId(),
            q.mode(),
            q.promptId(),
            q.authorMemberId(),
            q.from(),
            q.to(),
            q.page(),
            q.size());
    var items = attachments.many(q.petId(), answers.list(filter));
    long count = answers.count(filter);
    return new QuestionAnswersPage(
        items, q.page(), q.size(), count, count / q.size() + (count % q.size() == 0 ? 0 : 1));
  }

  public boolean exists(HasQuestionAnswersQuery q) {
    return answers.existsByPetId(q.petId());
  }
}
