package com.pawmento.question.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pawmento.question.application.exception.QuestionPersistenceUnavailableException;
import com.pawmento.question.domain.Question;
import com.pawmento.question.domain.QuestionMode;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;

class QuestionPersistenceFailureTest {
  @Test
  @DisplayName("#31-S26 질문 수정의 낙관적 충돌은 일시 장애 503으로 바꾸지 않고 409로 구분한다")
  void updateConflictIsNotStorageFailure() {
    var repository = mock(QuestionJpaRepository.class);
    var adapter = new QuestionPersistenceAdapter(repository);
    var now = Instant.parse("2026-11-23T00:00:00Z");
    var question = Question.restore(1L, QuestionMode.ACTIVE, "기존 질문", true, now, now, 0);
    var entity = QuestionJpaEntity.fromNew(Question.create(QuestionMode.ACTIVE, "기존 질문", now));
    org.springframework.test.util.ReflectionTestUtils.setField(entity, "id", 1L);
    when(repository.findById(1L)).thenReturn(java.util.Optional.of(entity));
    org.mockito.Mockito.doThrow(new org.springframework.orm.ObjectOptimisticLockingFailureException(QuestionJpaEntity.class, 1L))
        .when(repository).flush();
    assertThatThrownBy(() -> adapter.update(question.edit("바꾼 질문", false, now.plusSeconds(1))))
        .isInstanceOf(com.pawmento.question.application.exception.QuestionModificationConflictException.class);
  }

  @Test
  @DisplayName("#31-S24 신규 질문 저장의 일시 장애를 분류하고 기존 원본을 덮어쓰지 않는다")
  void createFailureAndExistingRowGuard() {
    var repository = mock(QuestionJpaRepository.class);
    var adapter = new QuestionPersistenceAdapter(repository);
    var now = Instant.parse("2026-11-23T00:00:00Z");
    var existing = Question.restore(1L, QuestionMode.ACTIVE, "기존 시드", true, now, now, 0);
    assertThatThrownBy(() -> adapter.save(existing)).isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(repository);
    var failure = new DataAccessResourceFailureException("private storage details");
    when(repository.saveAndFlush(any())).thenThrow(failure);
    assertThatThrownBy(() -> adapter.save(Question.create(QuestionMode.ACTIVE, "신규 질문", now)))
        .isInstanceOf(QuestionPersistenceUnavailableException.class).hasCause(failure)
        .hasMessageNotContaining("private storage details");
  }

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
