package com.pawmento.question.application.port.in;

public record GetCurrentQuestionPromptQuery(Long memberId, Long petId) {
  public GetCurrentQuestionPromptQuery {
    EnsureCurrentQuestionPromptCommand.validate(memberId, petId);
  }
}
