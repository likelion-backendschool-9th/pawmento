package com.pawmento.question.application.port.in;

import java.time.Instant;

public record CurrentQuestionPromptResult(Long petId, String mode, Prompt prompt) {
  public record Prompt(
      Long promptId, Long questionId, String question, long sequence, Instant assignedAt) {}
}
