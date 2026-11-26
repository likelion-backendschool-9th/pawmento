package com.pawmento.map.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.pawmento.map.application.port.in.StartPlaceSyncCommand;
import com.pawmento.map.application.port.out.PlaceSyncStatus;
import com.pawmento.map.application.port.out.PlaceSyncStatusPort;
import com.pawmento.map.domain.PlaceCategory;

// 시나리오는 docs/map/animal-places.md(#172-S22)에 있다. 접수·상태 전이·관리자 확인·중복 시작은 PlaceSyncAdminScenarioTest가 확인한다.
// 여기에는 요청으로 만들기 어려운, 실행기가 작업을 받지 못한 경우만 남긴다
class StartPlaceSyncServiceTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-24T00:00:00Z"), ZoneOffset.UTC);

	private final MemoryStatus statuses = new MemoryStatus();

	private static class MemoryStatus implements PlaceSyncStatusPort {

		final Map<PlaceCategory, PlaceSyncStatus> map = new EnumMap<>(PlaceCategory.class);

		@Override
		public boolean tryStart(PlaceCategory category, Instant startedAt) {
			PlaceSyncStatus current = map.get(category);
			if (current != null && current.state() == PlaceSyncStatus.State.RUNNING) {
				return false;
			}
			map.put(category, PlaceSyncStatus.running(startedAt));
			return true;
		}

		@Override
		public void finish(PlaceCategory category, PlaceSyncStatus status) {
			map.put(category, status);
		}

		@Override
		public Optional<PlaceSyncStatus> find(PlaceCategory category) {
			return Optional.ofNullable(map.get(category));
		}

	}

	@Test
	@DisplayName("#172-S22 실행을 맡기지 못하면 진행 중으로 남기지 않고 오류를 그대로 알린다")
	void notAcceptedDoesNotStayRunning() {
		var rejecting = new StartPlaceSyncService(memberId -> {
		}, statuses, task -> {
			throw new IllegalStateException("꽉 참");
		}, command -> null, CLOCK);

		assertThatThrownBy(() -> rejecting.start(new StartPlaceSyncCommand(1L, "HOSPITAL")))
			.isInstanceOf(IllegalStateException.class);

		assertThat(statuses.find(PlaceCategory.HOSPITAL).orElseThrow().state()).isEqualTo(PlaceSyncStatus.State.FAILED);
	}

}
