package com.pawmento.question.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.pawmento.question.application.exception.QuestionPersistenceUnavailableException;
import com.pawmento.question.domain.QuestionMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;

class QuestionPersistenceFailureTest {
  @Test
  @DisplayName("#31-S21 질문 조회의 일시 저장소 장애는 503 계열이고 무결성·프로그래밍 오류는 숨기지 않는다")
  void classifiesStorageFailures() {
    var repository = mock(QuestionJpaRepository.class);
    var adapter = new QuestionPersistenceAdapter(repository);
    var failure = new DataAccessResourceFailureException("private connection details");
    when(repository.findByModeAndEnabledTrueOrderByIdAsc(QuestionMode.ACTIVE)).thenThrow(failure);
    assertThatThrownBy(() -> adapter.loadByMode(QuestionMode.ACTIVE))
        .isInstanceOf(QuestionPersistenceUnavailableException.class)
        .hasCause(failure)
        .hasMessageNotContaining("private connection details");
    var invalid = new DataIntegrityViolationException("invalid storage");
    when(repository.findByModeAndEnabledTrueOrderByIdAsc(QuestionMode.GARDEN)).thenThrow(invalid);
    assertThatThrownBy(() -> adapter.loadByMode(QuestionMode.GARDEN)).isSameAs(invalid);
    assertThatThrownBy(() -> adapter.loadByMode(null)).isInstanceOf(IllegalArgumentException.class);
  }
}
