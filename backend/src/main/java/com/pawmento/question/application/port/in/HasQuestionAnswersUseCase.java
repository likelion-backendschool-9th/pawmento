package com.pawmento.question.application.port.in;

public interface HasQuestionAnswersUseCase {
  boolean exists(HasQuestionAnswersQuery command);
}
