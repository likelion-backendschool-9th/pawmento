package com.pawmento.question.application.service;

import com.pawmento.question.application.port.in.CurrentQuestionPromptResult;
import com.pawmento.question.domain.QuestionMode;
import com.pawmento.question.domain.QuestionPrompt;

final class CurrentQuestionPromptValues {
  private CurrentQuestionPromptValues() {}

  static CurrentQuestionPromptResult result(Long petId, QuestionMode mode, QuestionPrompt prompt) {
    return new CurrentQuestionPromptResult(
        petId,
        mode.name(),
        prompt == null
            ? null
            : new CurrentQuestionPromptResult.Prompt(
                prompt.getId(),
                prompt.getQuestionId(),
                prompt.getQuestionSnapshot(),
                prompt.getSequence(),
                prompt.getAssignedAt()));
  }
}
