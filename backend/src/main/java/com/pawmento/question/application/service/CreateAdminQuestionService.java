package com.pawmento.question.application.service;

import com.pawmento.question.application.port.in.AdminQuestionResult;
import com.pawmento.question.application.port.in.CreateAdminQuestionCommand;
import com.pawmento.question.application.port.in.CreateAdminQuestionUseCase;
import com.pawmento.question.application.port.out.CheckQuestionAdminAccessPort;
import com.pawmento.question.application.port.out.SaveQuestionPort;
import com.pawmento.question.domain.Question;
import com.pawmento.question.domain.QuestionMode;
import com.pawmento.question.domain.exception.InvalidQuestionException;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.REQUIRES_NEW)
public class CreateAdminQuestionService implements CreateAdminQuestionUseCase {
  private final CheckQuestionAdminAccessPort access;
  private final SaveQuestionPort questions;
  private final Clock clock;

  public AdminQuestionResult create(CreateAdminQuestionCommand command) {
    access.requireAdmin(command.memberId());
    QuestionMode mode;
    try {
      mode = command.mode() == null ? null : QuestionMode.valueOf(command.mode());
    } catch (IllegalArgumentException invalid) {
      throw new InvalidQuestionException();
    }
    var question = Question.create(mode, command.content(), clock.instant());
    return AdminQuestionResult.from(questions.save(question));
  }
}
