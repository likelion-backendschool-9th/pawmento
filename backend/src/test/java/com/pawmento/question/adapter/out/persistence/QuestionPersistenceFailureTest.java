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

  @Test
  @DisplayName("#31-S3 EntityManager 직접 잠금의 일시 장애도 503이며 무결성·계약 위반은 그대로 남긴다")
  void nativeLockFailurePolicy() {
    for (RuntimeException failure :
        new RuntimeException[] {
          new jakarta.persistence.LockTimeoutException("lock timeout"),
          new jakarta.persistence.QueryTimeoutException("query timeout"),
          new org.hibernate.exception.JDBCConnectionException(
              "lost", new java.sql.SQLException("connection")),
          new IllegalStateException("wrapped", new java.sql.SQLTransientConnectionException("pool"))
        }) {
      assertThatThrownBy(
              () ->
                  QuestionPersistenceOperations.execute(
                      () -> {
                        throw failure;
                      }))
          .isInstanceOf(QuestionPersistenceUnavailableException.class)
          .hasCause(failure);
    }
    var invalid = new DataIntegrityViolationException("broken relation");
    assertThatThrownBy(
            () ->
                QuestionPersistenceOperations.execute(
                    () -> {
                      throw invalid;
                    }))
        .isSameAs(invalid);
  }
}
