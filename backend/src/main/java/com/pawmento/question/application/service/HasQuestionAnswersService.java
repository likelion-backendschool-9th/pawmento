package com.pawmento.question.application.service;

import com.pawmento.question.application.port.in.*;
import com.pawmento.question.application.port.out.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HasQuestionAnswersService implements HasQuestionAnswersUseCase {
  private final QuestionAnswerStorePort answers;

  public boolean exists(HasQuestionAnswersQuery q) {
    return answers.existsByPetId(q.petId());
  }
}
