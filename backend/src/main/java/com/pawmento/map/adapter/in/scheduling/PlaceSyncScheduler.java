package com.pawmento.map.adapter.in.scheduling;

import java.time.Duration;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import com.pawmento.global.exception.StateConflictException;
import com.pawmento.global.exception.StorageUnavailableException;
import com.pawmento.map.application.exception.LocalDataSyncFailedException;
import com.pawmento.map.application.port.in.SyncLocalDataPlacesCommand;
import com.pawmento.map.application.port.in.SyncLocalDataPlacesResult;
import com.pawmento.map.application.port.in.SyncLocalDataPlacesUseCase;

/**
 * 매일 정해진 시각에 병원과 미용을, 매주 한 번 보호센터를 가져온다(#172-S22). 한 종류가 실패해도 다른 종류는 이어서 가져오고, 일시 장애는 정한 시간을
 * 두고 정한 횟수만큼 다시 시도한 뒤 포기한다(다음 날 다시 돈다). 이미 다른 실행이 돌고 있으면(관리자 수동 실행) 건너뛴다.
 */
class PlaceSyncScheduler {

	private static final Logger log = LoggerFactory.getLogger(PlaceSyncScheduler.class);

	private static final List<String> CATEGORIES = List.of("HOSPITAL", "GROOMING");

	private final SyncLocalDataPlacesUseCase useCase;

	private final PlaceSyncRunner runner;

	private final Duration retryDelay;

	private final int maxRetries;

	PlaceSyncScheduler(SyncLocalDataPlacesUseCase useCase, PlaceSyncRunner runner, Duration retryDelay,
			int maxRetries) {
		this.useCase = useCase;
		this.runner = runner;
		this.retryDelay = retryDelay;
		this.maxRetries = maxRetries;
	}

	@Scheduled(cron = "${pawmento.map.local-data.sync.cron:0 30 4 * * *}", zone = "Asia/Seoul")
	void trigger() {
		runner.runNow(() -> CATEGORIES.forEach(category -> attempt(category, 0)));
	}

	// 보호센터는 바뀌는 일이 드물어 주 1회만 가져온다(#172-S8)
	@Scheduled(cron = "${pawmento.map.local-data.sync.shelter-cron:0 50 4 * * MON}", zone = "Asia/Seoul")
	void triggerShelters() {
		runner.runNow(() -> attempt("SHELTER", 0));
	}

	private void attempt(String category, int retriesDone) {
		try {
			SyncLocalDataPlacesResult result = useCase.sync(new SyncLocalDataPlacesCommand(category));
			log.info("장소 자동 가져오기 완료: {} 읽음 {}, 저장 {}, 추가 {}, 갱신 {}, 삭제 {}", category, result.read(),
					result.saved(), result.inserted(), result.updated(), result.deleted());
		}
		catch (StateConflictException e) {
			log.info("장소 자동 가져오기 건너뜀(이미 가져오는 중): {}", category);
		}
		catch (LocalDataSyncFailedException e) {
			if (e.retryable()) {
				retryLater(category, retriesDone, e);
			}
			else {
				// 필수 항목이 빠진 응답이나 포털이 거부한 요청은 다시 해도 같다. 사람이 원인(원본 형식, 인증키·한도)을 고쳐야 한다
				log.error("장소 자동 가져오기 실패, 다시 시도해도 같아서 멈춥니다({}): {}", e.reason(), category, e);
			}
		}
		catch (StorageUnavailableException e) {
			retryLater(category, retriesDone, e);
		}
		catch (RuntimeException e) {
			// 프로그램 오류는 다시 해도 같아서 다시 시도하지 않는다. 다른 종류는 이어서 가져온다
			log.error("장소 자동 가져오기 중 예상 밖의 오류: {}", category, e);
		}
	}

	// 정한 횟수까지 시간을 두고 다시 시도하고, 다 쓰면 포기한다(다음 주기에 다시 돈다)
	private void retryLater(String category, int retriesDone, RuntimeException cause) {
		if (retriesDone < maxRetries) {
			log.warn("장소 자동 가져오기 실패, {} 뒤 다시 시도({}/{}): {}", retryDelay, retriesDone + 1, maxRetries, category, cause);
			runner.runLater(() -> attempt(category, retriesDone + 1), retryDelay);
		}
		else {
			log.error("장소 자동 가져오기 실패, 다시 시도를 모두 썼습니다: {}", category, cause);
		}
	}

}
