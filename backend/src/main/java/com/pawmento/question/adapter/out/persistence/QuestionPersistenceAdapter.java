package com.pawmento.question.adapter.out.persistence;

import com.pawmento.question.application.exception.QuestionPersistenceUnavailableException;
import com.pawmento.question.application.port.out.LoadActiveQuestionsPort;
import com.pawmento.question.domain.Question;
import com.pawmento.question.domain.QuestionMode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessException;
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
    try {
      return repository.findByModeAndEnabledTrueOrderByIdAsc(mode).stream()
          .map(QuestionJpaEntity::toDomain)
          .toList();
    } catch (TransientDataAccessException
        | RecoverableDataAccessException
        | DataAccessResourceFailureException cause) {
      throw new QuestionPersistenceUnavailableException(cause);
    }
  }
}
