package com.pawmento.question.application.port.out;

import com.pawmento.question.domain.QuestionMode;
import com.pawmento.question.domain.QuestionPrompt;
import java.util.List;
import java.util.Optional;

public interface LoadQuestionPromptHistoryPort {
  record Summary(QuestionPrompt prompt, long answerCount, Long myAnswerId) {}

  record Page(List<Summary> items, long totalElements) {}

  Page list(Long petId, QuestionMode mode, Long memberId, int page, int size);

  Optional<Summary> findSummary(Long petId, Long promptId, Long memberId);
}
