package com.pawmento.map.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.pawmento.map.application.port.in.GetCurrentWalkQuery;
import com.pawmento.map.application.port.in.GetCurrentWalkResult;
import com.pawmento.map.application.port.out.LoadWalkPointsPort;
import com.pawmento.map.application.port.out.LoadWalkPort;
import com.pawmento.map.domain.Walk;
import com.pawmento.map.domain.WalkPoint;

class GetCurrentWalkServiceTest {

	private static final Instant NOW = Instant.parse("2026-11-23T05:00:00Z");

	private final LoadWalkPort loadWalkPort = mock(LoadWalkPort.class);

	private final LoadWalkPointsPort loadWalkPointsPort = mock(LoadWalkPointsPort.class);

	private final GetCurrentWalkService service = new GetCurrentWalkService(loadWalkPort, loadWalkPointsPort,
			Clock.fixed(NOW, ZoneOffset.UTC));

	private static Walk walk(long id, Instant startedAt, Instant lastActivityAt) {
		return Walk.restore(id, 10L, 1L, startedAt, null, lastActivityAt);
	}

	@Test
	@DisplayName("#167-S1 지금에서 중단 시간(5분)을 뺀 시각을 기준으로 회원·반려동물의 진행 중인 산책을 찾고, 좌표를 한 번에 읽어 돌려준다")
	void readsTheWalkInProgressWithItsPoints() {
		Instant startedAt = NOW.minusSeconds(600);
		Walk walk = walk(5L, startedAt, NOW.minusSeconds(30));
		when(loadWalkPort.loadOpenWalkActiveSince(1L, 10L, NOW.minus(Walk.INTERRUPTED_AFTER)))
			.thenReturn(Optional.of(walk));
		when(loadWalkPointsPort.loadByWalkIds(anyCollection())).thenReturn(Map.of(5L,
				List.of(WalkPoint.of(5L, 37.5, 127.0, startedAt.plusSeconds(15)),
						WalkPoint.of(5L, 37.6, 127.1, startedAt.plusSeconds(30)))));

		GetCurrentWalkResult result = service.get(new GetCurrentWalkQuery(1L, 10L)).orElseThrow();

		assertThat(result.walkId()).isEqualTo(5L);
		assertThat(result.startedAt()).isEqualTo(startedAt);
		assertThat(result.endedAt()).isNull();
		assertThat(result.status()).isEqualTo("IN_PROGRESS");
		assertThat(result.points()).extracting(GetCurrentWalkResult.Point::latitude).containsExactly(37.5, 37.6);
		verify(loadWalkPointsPort).loadByWalkIds(List.of(5L));
	}

	@Test
	@DisplayName("#167-S11 좌표가 아직 없으면 points가 빈 목록인 산책을 돌려준다")
	void aWalkWithoutPointsHasAnEmptyList() {
		Walk walk = walk(5L, NOW.minusSeconds(120), NOW.minusSeconds(120));
		when(loadWalkPort.loadOpenWalkActiveSince(any(), any(), any())).thenReturn(Optional.of(walk));
		when(loadWalkPointsPort.loadByWalkIds(anyCollection())).thenReturn(Map.of(5L, List.of()));

		GetCurrentWalkResult result = service.get(new GetCurrentWalkQuery(1L, 10L)).orElseThrow();

		assertThat(result.status()).isEqualTo("IN_PROGRESS");
		assertThat(result.points()).isEmpty();
	}

	@Test
	@DisplayName("#167-S3 진행 중인 산책이 없으면 비어 있고 좌표는 읽지 않는다")
	void emptyWhenNothingIsInProgress() {
		when(loadWalkPort.loadOpenWalkActiveSince(any(), any(), any())).thenReturn(Optional.empty());

		assertThat(service.get(new GetCurrentWalkQuery(1L, 10L))).isEmpty();

		verify(loadWalkPointsPort, never()).loadByWalkIds(anyCollection());
	}

	@Test
	@DisplayName("#167-S3 마지막 활동이 정확히 5분 전이면 진행 중이고, 조금이라도 오래되면 저장소가 돌려주더라도 진행 중으로 보지 않는다")
	void fiveMinutesIsTheBoundary() {
		Walk exactlyFiveMinutes = walk(5L, NOW.minusSeconds(3600), NOW.minus(Walk.INTERRUPTED_AFTER));
		Walk justOver = walk(6L, NOW.minusSeconds(3600), NOW.minus(Walk.INTERRUPTED_AFTER).minusNanos(1000));
		when(loadWalkPointsPort.loadByWalkIds(anyCollection())).thenReturn(Map.of());

		when(loadWalkPort.loadOpenWalkActiveSince(any(), any(), any())).thenReturn(Optional.of(exactlyFiveMinutes));
		assertThat(service.get(new GetCurrentWalkQuery(1L, 10L))).isPresent();

		// 저장소의 조건과 도메인의 상태 판정이 어긋나더라도 중단된 산책을 진행 중으로 돌려주지 않는다
		when(loadWalkPort.loadOpenWalkActiveSince(any(), any(), any())).thenReturn(Optional.of(justOver));
		assertThat(service.get(new GetCurrentWalkQuery(1L, 10L))).isEmpty();
	}

}
