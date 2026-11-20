package com.pawmento.question.application.port.in;

public interface ListQuestionAnswersUseCase {
  QuestionAnswersPage list(ListQuestionAnswersQuery command);
}
