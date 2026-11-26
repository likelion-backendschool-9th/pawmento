package com.pawmento.map.adapter.out.persistence;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.pawmento.map.domain.Place;

import jakarta.annotation.PreDestroy;

/**
 * 검색용 열(name_key, address_key)을 더하기 전에 저장한 장소의 열을 서버가 시작할 때 채운다(#195-S19).
 * 비어 있는 행만 고치고 이미 채워진 행은 건드리지 않는다. 열이 비어 있지 않으면 읽는 행이 없어 바로 끝난다.
 *
 * <p>행을 읽은 뒤 저장하는 방식이 아니라, 갱신할 때 {@code name_key is null}을 다시 확인하는 조건 갱신으로 두 열만 채운다
 * ({@link PlaceJpaRepository#fillSearchKeys}). 읽은 뒤에 장소 가져오기가 같은 행의 이름·주소를 갱신했다면 가져오기가 이미 열을
 * 채웠으므로 아무것도 바꾸지 않아, 최신 장소 정보를 옛 값으로 되돌릴 일이 없다. 각 갱신은 한 문장이라 따로 트랜잭션을 묶지 않는다.
 *
 * <p>한 행에서 실패해도 나머지 행은 계속 채운다. 예외가 반복문 밖에서 처리되면 실패한 행 뒤의 장소가 모두 열이 빈 채로 서버가
 * 시작하고, 그 장소들은 검색에서 빠진다. 채우지 못한 행은 건너뛰고 로그에 남기며, 다음에 서버가 켜질 때 다시 시도한다.
 * 채우기가 통째로 실패해도(예: DB 연결) 서버 시작은 막지 않는다.
 *
 * <p>시작할 때 실패하거나 못 채운 행이 있으면 서버를 다시 켜기를 기다리지 않고 일정 간격으로 다시 시도한다(DB 연결이 잠시 끊겼다
 * 돌아온 경우 등). 다시 시도는 이 클래스만 쓰는 전용 스레드에서 돈다. 공용 {@code @Scheduled} 스레드는 미디어·엽서 같은 다른 예약 작업과 같이
 * 쓰므로 장소 1.5만 곳을 갱신하는 동안 그 작업들을 붙잡을 수 있고, 다른 기능의 스케줄링 설정(인증키가 없으면 꺼지는 등)에 따라 등록되지
 * 않을 수도 있다. 시도 횟수에는 상한이 있어 계속 실패하는 행이 있어도 로그를 끝없이 남기지 않는다.
 */
@Component
class PlaceSearchKeyBackfill implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(PlaceSearchKeyBackfill.class);

	/** 시작할 때 실패한 뒤 다시 시도하는 최대 횟수. 기본 간격 5분이면 한 시간 동안 시도한다 */
	static final int MAX_RETRIES = 12;

	private final PlaceJpaRepository placeJpaRepository;

	private final long retryDelayMs;

	// 다시 시도만 하는 전용 스레드. 서버를 끌 때 멈추도록 데몬 스레드로 둔다
	private final ScheduledExecutorService retryExecutor = Executors.newSingleThreadScheduledExecutor(task -> {
		Thread thread = new Thread(task, "place-search-key-backfill-retry");
		thread.setDaemon(true);
		return thread;
	});

	@Autowired
	PlaceSearchKeyBackfill(PlaceJpaRepository placeJpaRepository,
			@Value("${pawmento.map.search-key-backfill.retry-delay-ms:300000}") long retryDelayMs) {
		this.placeJpaRepository = placeJpaRepository;
		this.retryDelayMs = retryDelayMs;
	}

	/**
	 * @param filled 이번에 실제로 채운 행의 수
	 * @param failed 채우지 못한 행의 수. 이 행들은 열이 비어 있어 검색에 잡히지 않고, 다음에 켜질 때 다시 시도한다
	 */
	record Result(int filled, int failed) {
	}

	@Override
	public void run(ApplicationArguments args) {
		if (attempt()) {
			scheduleRetry(MAX_RETRIES);
		}
	}

	/** 다시 시도할 횟수가 남았으면 간격을 두고 전용 스레드에서 다시 채워 본다. 다 채웠으면 멈추고, 횟수를 다 쓰면 경고만 남기고 끝낸다. */
	private void scheduleRetry(int retriesLeft) {
		if (retriesLeft <= 0) {
			log.warn("장소의 검색용 열 채우기를 {}번 다시 시도했지만 다 채우지 못했습니다. 다음에 서버가 켜질 때 다시 시도합니다.", MAX_RETRIES);
			return;
		}
		retryExecutor.schedule(() -> {
			if (attempt()) {
				scheduleRetry(retriesLeft - 1);
			}
		}, retryDelayMs, TimeUnit.MILLISECONDS);
	}

	@PreDestroy
	void stopRetrying() {
		retryExecutor.shutdownNow();
	}

	/** 한 번 채워 보고, 다시 시도해야 하면(통째로 실패했거나 못 채운 행이 있으면) true. 예외는 밖으로 내보내지 않는다. */
	private boolean attempt() {
		try {
			Result result = backfill();
			if (result.filled() > 0) {
				log.info("장소 {}곳의 검색용 열을 채웠습니다.", result.filled());
			}
			if (result.failed() > 0) {
				log.warn("장소 {}곳의 검색용 열을 채우지 못했습니다. 그 장소는 검색에 잡히지 않아 다시 시도합니다.", result.failed());
			}
			return result.failed() > 0;
		}
		catch (RuntimeException e) {
			log.warn("장소의 검색용 열을 채우지 못했습니다. 다시 시도합니다: {}", e.getClass().getSimpleName());
			return true;
		}
	}

	/** 비어 있는 행의 검색용 열을 채운다. 한 행이 실패해도 나머지는 계속 채운다. */
	Result backfill() {
		int filled = 0;
		int failed = 0;
		long afterId = 0;
		List<PlaceJpaEntity> batch = placeJpaRepository.findTop500ByNameKeyIsNullAndIdGreaterThanOrderByIdAsc(afterId);
		while (!batch.isEmpty()) {
			for (PlaceJpaEntity entity : batch) {
				try {
					if (fill(entity)) {
						filled++;
					}
				}
				catch (RuntimeException e) {
					failed++;
					// 장소 id와 예외 종류만 남긴다. 이름·주소 같은 값은 남기지 않는다
					log.warn("장소(id={})의 검색용 열을 채우지 못했습니다: {}", entity.getId(), e.getClass().getSimpleName());
				}
			}
			// 다음 묶음은 이번 묶음의 마지막 id보다 큰 행만 읽는다. 채우지 못한 행이 있어도 반드시 앞으로 나아가 끝난다
			afterId = batch.get(batch.size() - 1).getId();
			batch = placeJpaRepository.findTop500ByNameKeyIsNullAndIdGreaterThanOrderByIdAsc(afterId);
		}
		return new Result(filled, failed);
	}

	/** 읽은 값으로 두 열을 채운다. 그 사이 다른 곳이 먼저 채웠으면 아무것도 바꾸지 않고 false다. */
	boolean fill(PlaceJpaEntity entity) {
		Place place = entity.toDomain();
		return placeJpaRepository.fillSearchKeys(entity.getId(), place.nameKey(), place.addressKey()) > 0;
	}

}
