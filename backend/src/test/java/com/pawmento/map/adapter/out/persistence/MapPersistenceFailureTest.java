package com.pawmento.map.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.domain.Pageable;

import com.pawmento.global.exception.StorageUnavailableException;
import com.pawmento.map.application.exception.RecordLocationPersistenceUnavailableException;
import com.pawmento.map.application.exception.WalkPersistenceUnavailableException;
import com.pawmento.map.domain.MapArea;
import com.pawmento.map.domain.RecordLocation;
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

	private final RecordLocationJpaRepository locations = mock(RecordLocationJpaRepository.class);

	private final WalkPersistenceAdapter walkAdapter = new WalkPersistenceAdapter(walks, points,
			mock(EntityManager.class));

	private final RecordLocationPersistenceAdapter locationAdapter = new RecordLocationPersistenceAdapter(locations);

	private final DataAccessResourceFailureException dbDown = new DataAccessResourceFailureException(
			"internal database details");

	private void expectWalkUnavailable(Runnable call) {
		assertThatThrownBy(call::run).isInstanceOf(WalkPersistenceUnavailableException.class)
			.isInstanceOf(StorageUnavailableException.class)
			.hasCause(dbDown)
			.hasMessageNotContaining("internal database details");
	}

	private void expectLocationUnavailable(Runnable call) {
		assertThatThrownBy(call::run).isInstanceOf(RecordLocationPersistenceUnavailableException.class)
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
		when(locations.findByRecordId(anyLong())).thenThrow(dbDown);
		when(locations.findInArea(anyLong(), anyLong(), anyDouble(), anyDouble(), anyDouble(), anyDouble(),
				any(Pageable.class)))
			.thenThrow(dbDown);

		MapArea area = MapArea.of(37.0, 126.0, 38.0, 128.0);
		expectLocationUnavailable(() -> locationAdapter.save(RecordLocation.create(1L, 10L, 1L, 37.5, 127.0, null)));
		expectLocationUnavailable(() -> locationAdapter.loadByRecordId(1L));
		expectLocationUnavailable(() -> locationAdapter.deleteByRecordId(1L));
		expectLocationUnavailable(() -> locationAdapter.loadInArea(1L, 10L, area, 10));
	}

	@Test
	@DisplayName("#72-S10 JPA 예외(PersistenceException)도 같은 예외로 바꾼다")
	void persistenceExceptionIsTranslated() {
		PersistenceException cause = new PersistenceException("entity manager closed");
		when(walks.findById(anyLong())).thenThrow(cause);

		assertThatThrownBy(() -> walkAdapter.loadById(1L)).isInstanceOf(WalkPersistenceUnavailableException.class)
			.hasCause(cause);
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
