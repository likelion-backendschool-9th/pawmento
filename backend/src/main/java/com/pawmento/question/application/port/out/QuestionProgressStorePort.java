package com.pawmento.question.application.port.out;

import com.pawmento.question.domain.QuestionMode;
import com.pawmento.question.domain.QuestionProgress;
import com.pawmento.question.domain.QuestionPrompt;
import java.util.Optional;

public interface QuestionProgressStorePort {
  QuestionProgress lock(Long petId, QuestionMode mode);

  Optional<QuestionPrompt> findCurrent(Long petId, QuestionMode mode);

  QuestionPrompt loadPrompt(Long promptId);

  Long lastQuestionId(Long petId, QuestionMode mode);

  Long savePrompt(QuestionPrompt prompt);

  void saveProgress(QuestionProgress progress);
}
