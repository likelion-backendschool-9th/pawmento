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

  /** 저장된 ID와 DB 시각 정밀도를 반영한 회차를 반환한다. */
  QuestionPrompt savePrompt(QuestionPrompt prompt);

  void saveProgress(QuestionProgress progress);
}
