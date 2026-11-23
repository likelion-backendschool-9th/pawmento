package com.pawmento.question.adapter.out.persistence;

import com.pawmento.question.application.exception.QuestionPersistenceUnavailableException;
import com.pawmento.question.application.exception.QuestionModificationConflictException;
import jakarta.persistence.LockTimeoutException;
import jakarta.persistence.QueryTimeoutException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTransientException;
import java.util.function.Supplier;
import org.hibernate.exception.JDBCConnectionException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessException;

final class QuestionPersistenceOperations {
  private QuestionPersistenceOperations() {}

  static <T> T execute(Supplier<T> action) {
    try {
      return action.get();
    } catch (RuntimeException failure) {
      // 이미 분류한 충돌의 기술 cause를 일시 장애로 다시 포장하지 않는다.
      if (failure instanceof QuestionModificationConflictException) throw failure;
      for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
        if (cause instanceof TransientDataAccessException
            || cause instanceof RecoverableDataAccessException
            || cause instanceof DataAccessResourceFailureException
            || cause instanceof LockTimeoutException
            || cause instanceof QueryTimeoutException
            || cause instanceof org.hibernate.exception.LockTimeoutException
            || cause instanceof JDBCConnectionException
            || cause instanceof SQLTransientException
            || cause instanceof SQLRecoverableException) {
          throw new QuestionPersistenceUnavailableException(failure);
        }
        if (cause.getCause() == cause) break;
      }
      throw failure;
    }
  }
}
