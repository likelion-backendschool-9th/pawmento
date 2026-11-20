package com.pawmento.question.application.port.in;

public interface GetCurrentQuestionPromptUseCase {
  CurrentQuestionPromptResult get(GetCurrentQuestionPromptQuery query);
}
