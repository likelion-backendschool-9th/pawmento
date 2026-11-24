package com.pawmento.map.application.service;

import java.time.Clock;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.pawmento.map.application.exception.LocalDataSyncFailedException;
import com.pawmento.map.application.exception.PlaceSyncAlreadyRunningException;
import com.pawmento.map.application.exception.PlaceSyncLockLostException;
import com.pawmento.map.application.port.in.GetPlaceSyncStatusQuery;
import com.pawmento.map.application.port.in.GetPlaceSyncStatusUseCase;
import com.pawmento.map.application.port.in.PlaceSyncStatusResult;
import com.pawmento.map.application.port.in.StartPlaceSyncCommand;
import com.pawmento.map.application.port.in.StartPlaceSyncUseCase;
import com.pawmento.map.application.port.in.SyncLocalDataPlacesCommand;
import com.pawmento.map.application.port.in.SyncLocalDataPlacesResult;
import com.pawmento.map.application.port.in.SyncLocalDataPlacesUseCase;
import com.pawmento.map.application.port.out.CheckPlaceSyncAdminPort;
import com.pawmento.map.application.port.out.PlaceSyncExecutorPort;
import com.pawmento.map.application.port.out.PlaceSyncStatus;
import com.pawmento.map.application.port.out.PlaceSyncStatusPort;
import com.pawmento.map.domain.PlaceCategory;

import lombok.RequiredArgsConstructor;

/**
 * 관리자의 수동 가져오기. 몇 분 걸리는 일을 요청 안에서 기다리면 nginx 시간 제한(60초)에 걸리므로, 접수만 하고 백그라운드에서 돌린 뒤
 * 진행·결과를 따로 조회하게 한다(#172-S22). 상태는 이 서버의 메모리에만 있다.
 */
@Service
@RequiredArgsConstructor
class StartPlaceSyncService implements StartPlaceSyncUseCase, GetPlaceSyncStatusUseCase {

	private static final Logger log = LoggerFactory.getLogger(StartPlaceSyncService.class);

	private final CheckPlaceSyncAdminPort adminPort;

	private final PlaceSyncStatusPort statusPort;

	private final PlaceSyncExecutorPort executorPort;

	private final SyncLocalDataPlacesUseCase syncUseCase;

	private final Clock clock;

	@Override
	public PlaceSyncStatusResult start(StartPlaceSyncCommand command) {
		PlaceCategory category = PlaceSyncCategories.toCategory(command.category());
		adminPort.requireAdmin(command.requestedBy());
		Instant startedAt = clock.instant();
		if (!statusPort.tryStart(category, startedAt)) {
			throw new PlaceSyncAlreadyRunningException();
		}
		try {
			executorPort.execute(() -> run(category, startedAt));
		}
		catch (RuntimeException e) {
			// 접수하지 못했는데 진행 중으로 남으면 다시는 시작할 수 없다
			statusPort.finish(category, PlaceSyncStatus.failed(startedAt, clock.instant(), "NOT_ACCEPTED"));
			throw e;
		}
		return toResult(category, PlaceSyncStatus.running(startedAt));
	}

	@Override
	public PlaceSyncStatusResult get(GetPlaceSyncStatusQuery query) {
		PlaceCategory category = PlaceSyncCategories.toCategory(query.category());
		adminPort.requireAdmin(query.requestedBy());
		return statusPort.find(category)
			.map(status -> toResult(category, status))
			.orElseGet(() -> new PlaceSyncStatusResult(category.name(), "NONE", null, null, null, null));
	}

	// 백그라운드 스레드에서 돈다. 어떤 예외든 끝 상태를 남겨야 진행 중으로 굳지 않는다
	private void run(PlaceCategory category, Instant startedAt) {
		PlaceSyncStatus finished;
		try {
			SyncLocalDataPlacesResult result = syncUseCase.sync(new SyncLocalDataPlacesCommand(null, category.name()));
			finished = PlaceSyncStatus.succeeded(startedAt, clock.instant(), result);
		}
		catch (PlaceSyncAlreadyRunningException e) {
			finished = PlaceSyncStatus.failed(startedAt, clock.instant(), "ALREADY_RUNNING");
		}
		catch (PlaceSyncLockLostException e) {
			log.error("장소 수동 가져오기 중 잠금을 잃어 저장하지 않았습니다: {}", category, e);
			finished = PlaceSyncStatus.failed(startedAt, clock.instant(), "LOCK_LOST");
		}
		catch (LocalDataSyncFailedException e) {
			log.error("장소 수동 가져오기 실패({}): {}", e.reason(), category, e);
			finished = PlaceSyncStatus.failed(startedAt, clock.instant(), e.reason().name());
		}
		catch (RuntimeException e) {
			log.error("장소 수동 가져오기 중 예상하지 못한 실패: {}", category, e);
			finished = PlaceSyncStatus.failed(startedAt, clock.instant(), "UNEXPECTED");
		}
		statusPort.finish(category, finished);
	}

	private static PlaceSyncStatusResult toResult(PlaceCategory category, PlaceSyncStatus status) {
		return new PlaceSyncStatusResult(category.name(), status.state().name(), status.startedAt(),
				status.finishedAt(), status.result(), status.failure());
	}

}
