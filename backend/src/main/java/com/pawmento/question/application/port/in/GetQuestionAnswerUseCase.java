package com.pawmento.question.application.port.in;

public interface GetQuestionAnswerUseCase {
  QuestionAnswerResult get(GetQuestionAnswerQuery command);
}
