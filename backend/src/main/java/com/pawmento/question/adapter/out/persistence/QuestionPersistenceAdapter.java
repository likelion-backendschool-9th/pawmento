package com.pawmento.question.adapter.out.persistence;

import com.pawmento.question.application.port.out.LoadActiveQuestionsPort;
import com.pawmento.question.domain.Question;
import com.pawmento.question.domain.QuestionMode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class QuestionPersistenceAdapter implements LoadActiveQuestionsPort {
  private final QuestionJpaRepository repository;

  @Override
  public List<Question> loadByMode(QuestionMode mode) {
    if (mode == null) {
      throw new IllegalArgumentException("질문 조회 모드가 필요합니다.");
    }
    return QuestionPersistenceOperations.execute(
        () ->
            repository.findByModeAndEnabledTrueOrderByIdAsc(mode).stream()
                .map(QuestionJpaEntity::toDomain)
                .toList());
  }
}
