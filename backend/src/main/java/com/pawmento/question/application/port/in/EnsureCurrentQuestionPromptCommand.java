package com.pawmento.question.application.port.in;

import com.pawmento.question.domain.exception.InvalidQuestionException;

public record EnsureCurrentQuestionPromptCommand(Long memberId, Long petId) {
  public EnsureCurrentQuestionPromptCommand {
    validate(memberId, petId);
  }

  static void validate(Long memberId, Long petId) {
    if (memberId == null || memberId <= 0 || petId == null || petId <= 0)
      throw new InvalidQuestionException();
  }
}
