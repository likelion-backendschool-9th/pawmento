package com.pawmento.map.adapter.out.persistence;

import java.sql.SQLRecoverableException;
import java.sql.SQLTransientException;
import java.util.function.Function;
import java.util.function.Supplier;

import org.hibernate.exception.JDBCConnectionException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessException;

import jakarta.persistence.LockTimeoutException;
import jakarta.persistence.QueryTimeoutException;

// 저장소 호출에서 나온 일시적인 저장소 장애(연결 장애, 타임아웃, 잠금 대기 초과 등)만 응용 예외(503)로 바꾼다.
// 원인은 cause로 보존한다. 그 밖의 예외는 바꾸지 않고 그대로 올려 500으로 남긴다:
// 우리가 던진 예외(IllegalStateException, 규칙 위반, 이미 있음), 프로그래밍 실수,
// 분류되지 않은 무결성 오류, 잘못된 JPA 호출(InvalidDataAccessApiUsageException)은 다시 시도해도 같은 결과이기 때문이다
final class MapPersistenceOperations {

	private MapPersistenceOperations() {
	}

	static <T> T execute(Supplier<T> action, Function<Throwable, RuntimeException> unavailable) {
		try {
			return action.get();
		}
		catch (RuntimeException e) {
			if (isTransientStorageFailure(e)) {
				throw unavailable.apply(e);
			}
			throw e;
		}
	}

	static void execute(Runnable action, Function<Throwable, RuntimeException> unavailable) {
		execute(() -> {
			action.run();
			return null;
		}, unavailable);
	}

	// 원인 사슬 어디에든 일시 장애 유형이 있으면 일시 장애로 본다. Spring이 감싸거나 Hibernate가 감싼 경우도 잡기 위해서다
	private static boolean isTransientStorageFailure(Throwable failure) {
		for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
			if (cause instanceof TransientDataAccessException || cause instanceof RecoverableDataAccessException
					|| cause instanceof DataAccessResourceFailureException || cause instanceof JDBCConnectionException
					|| cause instanceof QueryTimeoutException || cause instanceof LockTimeoutException
					|| cause instanceof SQLTransientException || cause instanceof SQLRecoverableException) {
				return true;
			}
			if (cause.getCause() == cause) {
				return false;
			}
		}
		return false;
	}

}
