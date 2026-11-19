package com.pawmento.map.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.pawmento.map.application.port.in.ListWalksQuery;
import com.pawmento.map.application.port.in.ListWalksResult;
import com.pawmento.map.application.port.out.LoadWalkPointsPort;
import com.pawmento.map.application.port.out.LoadWalkPort;
import com.pawmento.map.domain.Walk;
import com.pawmento.map.domain.WalkPoint;

class ListWalksServiceTest {

	private static final Instant START = Instant.parse("2026-11-17T01:00:00Z");

	private final LoadWalkPort loadWalkPort = mock(LoadWalkPort.class);

	private final LoadWalkPointsPort loadWalkPointsPort = mock(LoadWalkPointsPort.class);

	private ListWalksService service(Instant now) {
		return new ListWalksService(loadWalkPort, loadWalkPointsPort, Clock.fixed(now, ZoneOffset.UTC));
	}

	private static Walk walk(long id, Instant startedAt, Instant endedAt, Instant lastActivityAt) {
		return Walk.restore(id, 10L, 1L, startedAt, endedAt, lastActivityAt);
	}

	@Test
	@DisplayName("#73-S7 서울 날짜의 시작과 다음 날 0시 사이로 시작한 산책을 조회하고, 좌표는 한 번에 읽는다")
	void queriesSeoulDayAndReadsPointsOnce() {
		Walk first = walk(5L, START, START.plusSeconds(2400), START.plusSeconds(2400));
		Walk second = walk(6L, Instant.parse("2026-11-17T14:50:00Z"), null, Instant.parse("2026-11-17T15:20:00Z"));
		when(loadWalkPort.loadStartedBetween(1L, 10L, Instant.parse("2026-11-16T15:00:00Z"),
				Instant.parse("2026-11-17T15:00:00Z")))
			.thenReturn(List.of(first, second));
		when(loadWalkPointsPort.loadByWalkIds(anyCollection())).thenReturn(Map.of(5L,
				List.of(WalkPoint.of(5L, 37.5, 127.0, START.plusSeconds(15)),
						WalkPoint.of(5L, 37.6, 127.1, START.plusSeconds(30))),
				6L, List.of()));

		ListWalksResult result = service(Instant.parse("2026-11-19T00:00:00Z"))
			.list(new ListWalksQuery(1L, 10L, LocalDate.of(2026, 11, 17)));

		assertThat(result.walks()).extracting(ListWalksResult.Walk::walkId).containsExactly(5L, 6L);
		assertThat(result.walks().get(0).points()).extracting(ListWalksResult.Point::latitude)
			.containsExactly(37.5, 37.6);
		assertThat(result.walks().get(1).points()).isEmpty();
		// 산책마다 읽지 않고 한 번에 읽는다
		verify(loadWalkPointsPort).loadByWalkIds(List.of(5L, 6L));
		verify(loadWalkPointsPort, never()).loadByWalkId(anyLong());
	}

	@Test
	@DisplayName("#73-S7 상태는 지금 시각으로 계산한다. 끝낸 산책은 ENDED, 마지막 좌표가 5분을 넘은 산책은 INTERRUPTED이고 endedAt이 없다")
	void statusIsComputedAtNow() {
		Walk ended = walk(5L, START, START.plusSeconds(2400), START.plusSeconds(2400));
		Walk interrupted = walk(6L, START, null, START.plusSeconds(600));
		Walk inProgress = walk(7L, START, null, START.plusSeconds(3600));
		when(loadWalkPort.loadStartedBetween(any(), any(), any(), any()))
			.thenReturn(List.of(ended, interrupted, inProgress));
		when(loadWalkPointsPort.loadByWalkIds(anyCollection())).thenReturn(Map.of());

		// 마지막 활동이 START+3600인 산책은 4분 59초 뒤까지 진행 중이다
		ListWalksResult result = service(START.plusSeconds(3600 + 299))
			.list(new ListWalksQuery(1L, 10L, LocalDate.of(2026, 11, 17)));

		assertThat(result.walks()).extracting(ListWalksResult.Walk::status)
			.containsExactly("ENDED", "INTERRUPTED", "IN_PROGRESS");
		assertThat(result.walks().get(1).endedAt()).isNull();
		assertThat(result.walks().get(0).endedAt()).isEqualTo(START.plusSeconds(2400));
		// 좌표 조회 결과에 없는 산책은 빈 목록이다
		assertThat(result.walks().get(0).points()).isEmpty();
	}

	@Test
	@DisplayName("#73-S8 그 날 시작한 산책이 없으면 빈 목록이고 좌표를 읽지 않는다")
	void emptyDayDoesNotReadPoints() {
		when(loadWalkPort.loadStartedBetween(any(), any(), any(), any())).thenReturn(List.of());

		ListWalksResult result = service(START).list(new ListWalksQuery(1L, 10L, LocalDate.of(2026, 11, 17)));

		assertThat(result.walks()).isEmpty();
		verify(loadWalkPointsPort, never()).loadByWalkIds(anyCollection());
	}

}
