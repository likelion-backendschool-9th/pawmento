package com.pawmento.question.application.service;

import com.pawmento.question.application.exception.QuestionNotFoundException;
import com.pawmento.question.application.port.in.*;
import com.pawmento.question.application.port.out.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetAdminQuestionService implements GetAdminQuestionUseCase {
  private final CheckQuestionAdminAccessPort access;
  private final LoadQuestionPort questions;

  public AdminQuestionResult get(GetAdminQuestionQuery query) {
    access.requireAdmin(query.memberId());
    return AdminQuestionResult.from(questions.findById(query.questionId())
        .orElseThrow(QuestionNotFoundException::new));
  }
}
