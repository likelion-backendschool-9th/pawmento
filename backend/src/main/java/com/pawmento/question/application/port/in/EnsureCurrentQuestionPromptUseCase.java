package com.pawmento.question.application.port.in;

public interface EnsureCurrentQuestionPromptUseCase {
  EnsureCurrentQuestionPromptResult ensure(EnsureCurrentQuestionPromptCommand command);
}
