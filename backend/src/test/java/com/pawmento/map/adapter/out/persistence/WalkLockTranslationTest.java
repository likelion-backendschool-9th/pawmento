package com.pawmento.map.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.SQLException;

import org.hibernate.exception.JDBCConnectionException;
import org.hibernate.exception.LockTimeoutException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import com.pawmento.global.exception.StorageUnavailableException;
import com.pawmento.map.application.exception.WalkPersistenceUnavailableException;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.PessimisticLockException;

// 잠금 호출이 실패할 때 기술 예외가 서비스까지 올라가지 않고 응용 예외로 바뀌는지만 본다. 진짜 DB는 쓰지 않는다
class WalkLockTranslationTest {

	private final EntityManager entityManager = mock(EntityManager.class);

	private final WalkPersistenceAdapter adapter = new WalkPersistenceAdapter(mock(WalkJpaRepository.class),
			mock(WalkPointJpaRepository.class), entityManager);

	@Test
	@DisplayName("#73-S12 잠금 호출에서 DB 연결 장애(JPA 예외)가 나면 저장소 일시 장애 예외로 바꾼다")
	void persistenceExceptionIsTranslated() {
		PersistenceException cause = new JDBCConnectionException("db down", new SQLException("connection lost"));
		when(entityManager.createNativeQuery(anyString(), any(Class.class))).thenThrow(cause);

		assertThatThrownBy(() -> adapter.lock(10L)).isInstanceOf(WalkPersistenceUnavailableException.class)
			.isInstanceOf(StorageUnavailableException.class)
			.hasCause(cause);
	}

	@Test
	@DisplayName("#72-S10 잠금 대기 시간 초과(SQLSTATE 55P03)는 Hibernate LockTimeoutException을 PessimisticLockException이 감싼 모양이어도 일시 장애로 바꾸고 원인을 보존한다")
	void postgresLockTimeoutIsTranslated() {
		LockTimeoutException hibernateFailure = new LockTimeoutException("lock timeout", new SQLException("55P03"));
		PessimisticLockException cause = new PessimisticLockException("could not obtain lock", hibernateFailure);
		when(entityManager.createNativeQuery(anyString(), any(Class.class))).thenThrow(cause);

		assertThatThrownBy(() -> adapter.lock(10L)).isInstanceOf(WalkPersistenceUnavailableException.class)
			.hasCause(cause);
	}

	@Test
	@DisplayName("#72-S10 잠금 호출에서 분류되지 않은 JPA 예외는 일시 장애로 바꾸지 않고 그대로 올린다")
	void unclassifiedPersistenceExceptionPassesThrough() {
		PersistenceException cause = new PersistenceException("unclassified");
		when(entityManager.createNativeQuery(anyString(), any(Class.class))).thenThrow(cause);

		assertThatThrownBy(() -> adapter.lock(10L)).isSameAs(cause);
	}

	@Test
	@DisplayName("#73-S12 잠금 호출에서 스프링 데이터 접근 예외가 나도 같은 예외로 바꾼다")
	void dataAccessExceptionIsTranslated() {
		when(entityManager.createNativeQuery(anyString(), any(Class.class)))
			.thenThrow(new DataAccessResourceFailureException("connection lost"));

		assertThatThrownBy(() -> adapter.lock(10L)).isInstanceOf(WalkPersistenceUnavailableException.class);
	}

}
