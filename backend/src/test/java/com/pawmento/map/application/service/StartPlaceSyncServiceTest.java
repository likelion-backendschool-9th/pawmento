package com.pawmento.map.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.pawmento.map.application.exception.PublicDataSyncFailedException;
import com.pawmento.map.application.exception.PlaceSyncAdminDeniedException;
import com.pawmento.map.application.exception.PlaceSyncAlreadyRunningException;
import com.pawmento.map.application.exception.PlaceSyncLockLostException;
import com.pawmento.map.application.port.in.GetPlaceSyncStatusQuery;
import com.pawmento.map.application.port.in.StartPlaceSyncCommand;
import com.pawmento.map.application.port.in.SyncPublicDataPlacesResult;
import com.pawmento.map.application.port.out.PlaceSyncStatus;
import com.pawmento.map.application.port.out.PlaceSyncStatusPort;
import com.pawmento.map.domain.PlaceCategory;

// 시나리오는 docs/map/animal-places.md(#172-S22)에 있다. 접수·완료·관리자 확인·NONE은 PlaceSyncAdminScenarioTest가 확인한다.
// 여기에는 요청으로 만들기 어려운 경우(다른 종류가 RUNNING인 동안의 접수, LOCK_LOST·UNEXPECTED 실패, 실행기가 작업을 받지 못함)만 남긴다
class StartPlaceSyncServiceTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-24T00:00:00Z"), ZoneOffset.UTC);

	private final List<Runnable> queued = new ArrayList<>();

	private final List<Long> checked = new ArrayList<>();

	private boolean admin = true;

	private RuntimeException failure;

	private final MemoryStatus statuses = new MemoryStatus();

	private final StartPlaceSyncService service = new StartPlaceSyncService(memberId -> {
		checked.add(memberId);
		if (!admin) {
			throw new PlaceSyncAdminDeniedException();
		}
	}, statuses, queued::add, command -> {
		if (failure != null) {
			throw failure;
		}
		return new SyncPublicDataPlacesResult(command.category(), 10, 8, 2, 1, 1, 0, 8, 0, 0);
	}, CLOCK);

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
	@DisplayName("#172-S22 진행 중인 종류를 다시 시작하면 거절하고, 다른 종류나 끝난 뒤에는 시작할 수 있다")
	void secondStartWhileRunningIsRejected() {
		service.start(new StartPlaceSyncCommand(1L, "HOSPITAL"));

		assertThatThrownBy(() -> service.start(new StartPlaceSyncCommand(1L, "HOSPITAL")))
			.isInstanceOf(PlaceSyncAlreadyRunningException.class);
		assertThat(service.start(new StartPlaceSyncCommand(1L, "GROOMING")).state()).isEqualTo("RUNNING");

		queued.get(0).run();
		assertThat(service.start(new StartPlaceSyncCommand(1L, "HOSPITAL")).state()).isEqualTo("RUNNING");
	}

	@Test
	@DisplayName("#172-S22 실행이 실패하면 이유가 있는 FAILED로 남고 진행 중으로 굳지 않는다")
	void failuresAreRecordedWithAReason() {
		for (var entry : Map.of(
				"FETCH_FAILED", PublicDataSyncFailedException.fetchFailed(new RuntimeException("시간 초과")),
				"LOCK_LOST", new PlaceSyncLockLostException(new RuntimeException("끊김")),
				"ALREADY_RUNNING", new PlaceSyncAlreadyRunningException(),
				"UNEXPECTED", new IllegalStateException("버그")).entrySet()) {
			failure = entry.getValue();
			queued.clear();
			service.start(new StartPlaceSyncCommand(1L, "HOSPITAL"));
			queued.get(0).run();

			var status = service.get(new GetPlaceSyncStatusQuery(1L, "HOSPITAL"));
			assertThat(status.state()).as(entry.getKey()).isEqualTo("FAILED");
			assertThat(status.failure()).isEqualTo(entry.getKey());
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
