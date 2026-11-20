package com.pawmento.question.application.port.in;

public interface CreateQuestionAnswerUseCase {
  CreateQuestionAnswerResult create(CreateQuestionAnswerCommand command);
}
