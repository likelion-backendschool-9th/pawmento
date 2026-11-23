package com.pawmento.question.application.service;

import com.pawmento.question.application.exception.QuestionModificationConflictException;
import com.pawmento.question.application.exception.QuestionNotFoundException;
import com.pawmento.question.application.port.in.*;
import com.pawmento.question.application.port.out.*;
import com.pawmento.question.domain.exception.InvalidQuestionException;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.REQUIRES_NEW)
public class UpdateAdminQuestionService implements UpdateAdminQuestionUseCase {
  private final CheckQuestionAdminAccessPort access;
  private final LoadQuestionPort questions;
  private final UpdateQuestionPort updates;
  private final Clock clock;

  public AdminQuestionResult update(UpdateAdminQuestionCommand command) {
    access.requireAdmin(command.memberId());
    if (command.questionId() == null || command.questionId() <= 0 || command.version() < 0)
      throw new InvalidQuestionException();
    var current = questions.findById(command.questionId()).orElseThrow(QuestionNotFoundException::new);
    if (current.getVersion() != command.version()) throw new QuestionModificationConflictException();
    var edited = current.edit(command.content(), command.enabled(), clock.instant());
    if (edited == current) return AdminQuestionResult.from(current);
    return AdminQuestionResult.from(updates.update(edited));
  }
}
