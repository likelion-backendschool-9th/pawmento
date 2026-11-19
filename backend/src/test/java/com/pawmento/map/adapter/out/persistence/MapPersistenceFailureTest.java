package com.pawmento.map.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.time.Instant;
import java.util.List;

import org.hibernate.exception.JDBCConnectionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.domain.Pageable;

import com.pawmento.global.exception.StorageUnavailableException;
import com.pawmento.map.application.exception.DiaryLocationPersistenceUnavailableException;
import com.pawmento.map.application.exception.WalkPersistenceUnavailableException;
import com.pawmento.map.domain.MapArea;
import com.pawmento.map.domain.DiaryLocation;
import com.pawmento.map.domain.Walk;
import com.pawmento.map.domain.WalkPoint;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;

// 저장소 메서드마다 DB 장애가 서비스까지 올라가지 않고 응용 예외(503)로 바뀌고 원인이 보존되는지 본다.
// 진짜 DB는 쓰지 않는다. 우리가 던지는 예외와 프로그래밍 실수는 바꾸지 않는 것도 함께 본다
class MapPersistenceFailureTest {

	private static final Instant AT = Instant.parse("2026-11-18T01:00:00Z");

	private final WalkJpaRepository walks = mock(WalkJpaRepository.class);

	private final WalkPointJpaRepository points = mock(WalkPointJpaRepository.class);

	private final DiaryLocationJpaRepository locations = mock(DiaryLocationJpaRepository.class);

	private final WalkPersistenceAdapter walkAdapter = new WalkPersistenceAdapter(walks, points,
			mock(EntityManager.class));

	private final DiaryLocationPersistenceAdapter locationAdapter = new DiaryLocationPersistenceAdapter(locations);

	private final DataAccessResourceFailureException dbDown = new DataAccessResourceFailureException(
			"internal database details");

	private void expectWalkUnavailable(Runnable call) {
		assertThatThrownBy(call::run).isInstanceOf(WalkPersistenceUnavailableException.class)
			.isInstanceOf(StorageUnavailableException.class)
			.hasCause(dbDown)
			.hasMessageNotContaining("internal database details");
	}

	private void expectLocationUnavailable(Runnable call) {
		assertThatThrownBy(call::run).isInstanceOf(DiaryLocationPersistenceUnavailableException.class)
			.isInstanceOf(StorageUnavailableException.class)
			.hasCause(dbDown)
			.hasMessageNotContaining("internal database details");
	}

	@Test
	@DisplayName("#72-S10 산책 저장소의 모든 메서드는 DB 장애를 저장소 일시 장애 예외로 바꾸고 원인을 보존한다")
	void walkAdapterTranslatesEveryMethod() {
		when(walks.save(any())).thenThrow(dbDown);
		when(walks.findById(anyLong())).thenThrow(dbDown);
		when(walks.findForUpdate(anyLong())).thenThrow(dbDown);
		when(walks.findStartedBetween(anyLong(), anyLong(), any(), any())).thenThrow(dbDown);
		when(walks.existsByPetIdAndEndedAtIsNullAndLastActivityAtGreaterThanEqual(anyLong(), any()))
			.thenThrow(dbDown);
		when(points.insertIfAbsent(anyLong(), anyDouble(), anyDouble(), any())).thenThrow(dbDown);
		when(points.findByWalkIdOrderByRecordedAtAsc(anyLong())).thenThrow(dbDown);
		when(points.countByWalkId(anyLong())).thenThrow(dbDown);

		expectWalkUnavailable(() -> walkAdapter.save(Walk.start(10L, 1L, AT)));
		expectWalkUnavailable(() -> walkAdapter.loadById(1L));
		expectWalkUnavailable(() -> walkAdapter.loadByIdForUpdate(1L));
		expectWalkUnavailable(() -> walkAdapter.loadStartedBetween(1L, 10L, AT, AT.plusSeconds(60)));
		expectWalkUnavailable(() -> walkAdapter.existsOpenWalkActiveSince(10L, AT));
		expectWalkUnavailable(() -> walkAdapter.insertIfAbsent(List.of(WalkPoint.of(1L, 37.5, 127.0, AT))));
		expectWalkUnavailable(() -> walkAdapter.loadByWalkId(1L));
		expectWalkUnavailable(() -> walkAdapter.countByWalkId(1L));
	}

	@Test
	@DisplayName("#72-S10 기록 위치 저장소의 모든 메서드는 DB 장애를 저장소 일시 장애 예외로 바꾸고 원인을 보존한다")
	void locationAdapterTranslatesEveryMethod() {
		when(locations.save(any())).thenThrow(dbDown);
		when(locations.findByDiaryId(anyLong())).thenThrow(dbDown);
		when(locations.findInArea(anyLong(), anyLong(), anyDouble(), anyDouble(), anyDouble(), anyDouble(),
				any(Pageable.class)))
			.thenThrow(dbDown);

		MapArea area = MapArea.of(37.0, 126.0, 38.0, 128.0);
		expectLocationUnavailable(() -> locationAdapter.save(DiaryLocation.create(1L, 10L, 1L, 37.5, 127.0, null)));
		expectLocationUnavailable(() -> locationAdapter.loadByDiaryId(1L));
		expectLocationUnavailable(() -> locationAdapter.deleteByDiaryId(1L));
		expectLocationUnavailable(() -> locationAdapter.loadInArea(1L, 10L, area, 10));
	}

	@Test
	@DisplayName("#72-S10 일시적인 장애(연결 장애, 쿼리 타임아웃, 잠금 대기 초과, 일시적 JDBC 오류)만 저장소 일시 장애로 바꾼다")
	void onlyTransientFailuresAreUnavailable() {
		RuntimeException[] transients = { new QueryTimeoutException("timeout"),
				new PessimisticLockingFailureException("lock wait timeout"),
				new JDBCConnectionException("connection lost", new SQLException("08006")),
				new jakarta.persistence.QueryTimeoutException("jpa timeout"),
				// 원인 사슬 안에 있는 일시적 JDBC 오류도 잡는다
				new InvalidDataAccessApiUsageException("wrapped",
						new SQLTransientConnectionException("pool timeout")) };
		for (RuntimeException failure : transients) {
			// 같은 호출을 다시 스터빙하므로 when(...) 대신 doThrow를 쓴다(when 안의 호출이 이전 예외를 던진다)
			doThrow(failure).when(walks).findById(1L);

			assertThatThrownBy(() -> walkAdapter.loadById(1L)).as(failure.getClass().getSimpleName())
				.isInstanceOf(WalkPersistenceUnavailableException.class)
				.hasCause(failure);
		}
	}

	@Test
	@DisplayName("#72-S10 분류되지 않은 무결성 오류, 잘못된 JPA 호출, 분류되지 않은 JPA 예외는 503으로 바꾸지 않고 그대로 올린다")
	void nonTransientFailuresPassThrough() {
		RuntimeException[] others = { new DataIntegrityViolationException("not null violated"),
				new InvalidDataAccessApiUsageException("bad jpa call", new IllegalArgumentException("bad")),
				new PersistenceException("unclassified") };
		for (RuntimeException failure : others) {
			doThrow(failure).when(walks).findById(1L);
			doThrow(failure).when(locations).findByDiaryId(1L);

			assertThatThrownBy(() -> walkAdapter.loadById(1L)).as(failure.getClass().getSimpleName())
				.isSameAs(failure);
			assertThatThrownBy(() -> locationAdapter.loadByDiaryId(1L)).isSameAs(failure);
		}
		// 기록 위치 저장: 유일 제약이 아닌 무결성 오류는 이미 있다는 오류도, 503도 아니다
		DataIntegrityViolationException other = new DataIntegrityViolationException("not null violated");
		when(locations.save(any())).thenThrow(other);

		assertThatThrownBy(() -> locationAdapter.save(DiaryLocation.create(1L, 10L, 1L, 37.5, 127.0, null)))
			.isSameAs(other);
	}

	@Test
	@DisplayName("#72-S10 프로그래밍 실수와 우리가 던진 예외는 저장소 장애로 바꾸지 않는다")
	void programmingErrorsPassThrough() {
		IllegalArgumentException bug = new IllegalArgumentException("unexpected bug");
		when(walks.findById(1L)).thenThrow(bug);
		Walk saved = Walk.restore(5L, 10L, 1L, AT, null, AT);
		when(walks.findById(5L)).thenReturn(java.util.Optional.empty());

		assertThatThrownBy(() -> walkAdapter.loadById(1L)).isSameAs(bug);
		// 저장된 산책이 없는데 갱신하려는 경우는 우리가 던지는 IllegalStateException이다
		assertThatThrownBy(() -> walkAdapter.save(saved)).isInstanceOf(IllegalStateException.class);
	}

}
