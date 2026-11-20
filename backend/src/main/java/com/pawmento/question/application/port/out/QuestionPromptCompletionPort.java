package com.pawmento.question.application.port.out;

import com.pawmento.question.domain.QuestionPrompt;
import java.util.Optional;

public interface QuestionPromptCompletionPort {
  Optional<QuestionPrompt> find(Long petId, Long promptId);

  void complete(QuestionPrompt prompt);
}
