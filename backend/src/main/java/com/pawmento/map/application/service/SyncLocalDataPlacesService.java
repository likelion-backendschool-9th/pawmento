package com.pawmento.map.application.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.pawmento.map.application.exception.LocalDataSyncFailedException;
import com.pawmento.map.application.exception.LocalDataSyncFailedException.Reason;
import com.pawmento.map.application.exception.PlaceSyncAlreadyRunningException;
import com.pawmento.map.application.port.in.SyncLocalDataPlacesCommand;
import com.pawmento.map.application.port.in.SyncLocalDataPlacesResult;
import com.pawmento.map.application.port.in.SyncLocalDataPlacesUseCase;
import com.pawmento.map.application.port.out.AppliedPlaces;
import com.pawmento.map.application.port.out.CheckPlaceSyncAdminPort;
import com.pawmento.map.application.port.out.LoadLocalDataPort;
import com.pawmento.map.application.port.out.LoadShelterDataPort;
import com.pawmento.map.application.port.out.LocalDataSnapshot;
import com.pawmento.map.application.port.out.PlaceSyncLockPort;
import com.pawmento.map.application.port.out.ShelterSnapshot;
import com.pawmento.map.application.port.out.StorePlacesPort;
import com.pawmento.map.domain.LocalDataExclusion;
import com.pawmento.map.domain.LocalDataPlaceFactory;
import com.pawmento.map.domain.LocalDataRecord;
import com.pawmento.map.domain.PlaceCategory;
import com.pawmento.map.domain.PlaceDraft;
import com.pawmento.map.domain.ShelterPlaceFactory;
import com.pawmento.map.domain.ShelterRecord;

import lombok.RequiredArgsConstructor;

/**
 * 공공데이터(병원·미용·보호센터)를 받아 장소를 통째로 맞춘다. 받는 데 몇 분이 걸려서 받기(네트워크)는 트랜잭션 밖에서 하고, 믿을 수
 * 있는 전체 스냅샷일 때만 저장소 포트가 한 트랜잭션으로 반영한다(#172-S4, S5, S9, S20).
 */
@Service
@RequiredArgsConstructor
class SyncLocalDataPlacesService implements SyncLocalDataPlacesUseCase {

	private static final Logger log = LoggerFactory.getLogger(SyncLocalDataPlacesService.class);

	private final LoadLocalDataPort loadLocalDataPort;

	private final LoadShelterDataPort loadShelterDataPort;

	private final StorePlacesPort storePlacesPort;

	private final PlaceSyncLockPort lockPort;

	private final CheckPlaceSyncAdminPort adminPort;

	// 거른 뒤 저장할 건수가 기존 건수의 몇 % 이상이어야 반영하는가. 병원·미용은 totalCount와 고유 수가 같은지도 보므로 절반이면 되지만,
	// 보호센터는 totalCount를 믿지 못해 이 비율이 유일한 안전장치라 좁게 둔다. 센터는 주 1회 갱신되고 거의 바뀌지 않아, 일부만 받은
	// 응답으로 센터를 대량 삭제하는 것을 막는다(#172-S9)
	private static final int PERMIT_MIN_KEEP_PERCENT = 50;

	private static final int SHELTER_MIN_KEEP_PERCENT = 90;

	// 관리자가 요청했으면 현재 관리자인지 먼저 확인하고, 같은 종류를 이미 가져오는 중이면 받지 않고 거절한다. 잠금은 받기 전에 잡아서
	// 저장이 끝난 뒤에 놓는다(#172-S22)
	// 잠금은 DB 연결 하나에 걸린 세션 잠금이라, 바깥 트랜잭션 안에서 불리면 잠금 수명과 트랜잭션 수명이 어긋난다. 그래서 트랜잭션
	// 안에서 부르면 예외로 막는다
	@Override
	@Transactional(propagation = Propagation.NEVER)
	public SyncLocalDataPlacesResult sync(SyncLocalDataPlacesCommand command) {
		PlaceCategory category = PlaceSyncCategories.toCategory(command.category());
		if (command.requestedBy() != null) {
			adminPort.requireAdmin(command.requestedBy());
		}
		try (PlaceSyncLockPort.SyncLock lock = lockPort.tryAcquire(category)
			.orElseThrow(PlaceSyncAlreadyRunningException::new)) {
			return category == PlaceCategory.SHELTER ? syncShelters(lock) : syncPermits(category, lock);
		}
	}

	// 병원·미용(인허가). 페이지를 끝까지 받고 관리번호로 합친 고유 수가 totalCount와 같을 때만 믿는다
	private SyncLocalDataPlacesResult syncPermits(PlaceCategory category, PlaceSyncLockPort.SyncLock lock) {
		LocalDataSnapshot snapshot = loadLocalDataPort.load(category);

		// 수집하는 동안 원본이 바뀌었으면 어느 시점의 전체인지 알 수 없다
		if (snapshot.totalCountAtStart() != snapshot.totalCountAtEnd()) {
			throw LocalDataSyncFailedException.rejected(Reason.TOTAL_COUNT_CHANGED);
		}
		// 필수 항목이 빠졌으면 원본 형식이 바뀐 것일 수 있다. 일부만 믿고 저장하지 않는다
		if (snapshot.records().stream().anyMatch(record -> !record.isComplete())) {
			throw LocalDataSyncFailedException.rejected(Reason.MISSING_FIELDS);
		}
		// 정렬이 보장되지 않아 페이지가 겹치면 같은 행이 두 번 온다. 관리번호로 합친 고유 수가 totalCount와 같아야 빠진 행이 없다
		Map<String, LocalDataRecord> unique = new LinkedHashMap<>();
		// 저장 키는 LocalDataPlaceFactory가 앞뒤 공백을 지운 관리번호라서, 고유 수도 같은 값으로 센다. 공백만 다른 `a`와 `a `를 둘로
		// 세면 totalCount 검사는 통과하는데 저장소에 같은 관리번호가 두 번 넘어간다
		snapshot.records().forEach(record -> unique.putIfAbsent(record.sourceId().strip(), record));
		if (unique.size() != snapshot.totalCountAtStart()) {
			throw LocalDataSyncFailedException.rejected(Reason.TOTAL_COUNT_MISMATCH);
		}

		Tally tally = new Tally();
		for (LocalDataRecord record : unique.values()) {
			tally.add(LocalDataPlaceFactory.create(category, record));
		}
		return apply(category, unique.size(), tally, PERMIT_MIN_KEEP_PERCENT, lock);
	}

	// 보호센터. totalCount는 실제 행 수와 달라 믿지 않고, 한 번에 크게 요청해 받은 건수가 요청한 수보다 적을 때만 끝까지 받은 것으로 본다
	private SyncLocalDataPlacesResult syncShelters(PlaceSyncLockPort.SyncLock lock) {
		ShelterSnapshot snapshot = loadShelterDataPort.load();
		List<ShelterRecord> records = snapshot.records();

		// 서비스 오류로 빈 응답이 올 수 있다. 센터가 모두 사라졌다고 보지 않는다
		if (records.isEmpty()) {
			throw LocalDataSyncFailedException.rejected(Reason.EMPTY_SNAPSHOT);
		}
		// 요청한 만큼 꽉 차게 왔으면 뒤에 더 있을 수 있다
		if (records.size() >= snapshot.requestedRows()) {
			throw LocalDataSyncFailedException.rejected(Reason.POSSIBLY_TRUNCATED);
		}
		if (records.stream().anyMatch(record -> !record.isComplete())) {
			// 무엇이 빠졌는지 건수만 남긴다(값은 남기지 않는다). 원본 형식이 바뀐 것인지 가려내는 데 쓴다
			log.warn("보호센터 응답에 필수 항목이 빠진 행이 있습니다: 전체 {}, 등록번호 없음 {}, 이름 없음 {}", records.size(),
					records.stream().filter(r -> r.careRegNo() == null || r.careRegNo().isBlank()).count(),
					records.stream().filter(r -> r.name() == null || r.name().isBlank()).count());
			throw LocalDataSyncFailedException.rejected(Reason.MISSING_FIELDS);
		}
		Set<String> ids = new HashSet<>();
		for (ShelterRecord record : records) {
			if (!ids.add(record.careRegNo().strip())) {
				throw LocalDataSyncFailedException.rejected(Reason.DUPLICATE_IDS);
			}
		}

		Tally tally = new Tally();
		for (ShelterRecord record : records) {
			tally.add(ShelterPlaceFactory.create(record));
		}
		// 모든 행이 좌표가 없어 장소가 하나도 안 남으면 원본의 좌표 항목 이름이 바뀐 것일 수 있어 지도를 비우지 않는다
		if (tally.drafts.isEmpty()) {
			throw LocalDataSyncFailedException.rejected(Reason.TOO_FEW_PLACES);
		}
		return apply(PlaceCategory.SHELTER, records.size(), tally, SHELTER_MIN_KEEP_PERCENT, lock);
	}

	// 거른 뒤 저장할 건수가 기존 건수의 minKeepPercent% 미만이면 불완전한 수집일 수 있어 지도를 비우지 않는다
	private SyncLocalDataPlacesResult apply(PlaceCategory category, int read, Tally tally, int minKeepPercent,
			PlaceSyncLockPort.SyncLock lock) {
		int existing = storePlacesPort.countByCategory(category);
		if (existing > 0 && (long) tally.drafts.size() * 100 < (long) existing * minKeepPercent) {
			throw LocalDataSyncFailedException.rejected(Reason.TOO_FEW_PLACES);
		}
		// 받는 몇 분 사이에 잠금 연결이 끊겼다면 다른 실행이 같이 도는 중일 수 있다. 저장 직전에 아직 쥐고 있는지 확인한다
		lock.requireHeld();
		AppliedPlaces applied = storePlacesPort.replace(category, tally.drafts);
		return new SyncLocalDataPlacesResult(category.name(), read, tally.drafts.size(), tally.excluded(), tally.closed,
				tally.noCoordinates, tally.outOfRange, applied.inserted(), applied.updated(), applied.deleted());
	}

	// 장소가 된 행과 제외한 행을 이유별로 센다
	private static final class Tally {

		final List<PlaceDraft> drafts = new ArrayList<>();

		int closed;

		int noCoordinates;

		int outOfRange;

		void add(LocalDataPlaceFactory.Outcome outcome) {
			if (!outcome.excluded()) {
				drafts.add(outcome.draft());
			}
			else if (outcome.exclusion() == LocalDataExclusion.CLOSED) {
				closed++;
			}
			else if (outcome.exclusion() == LocalDataExclusion.NO_COORDINATES) {
				noCoordinates++;
			}
			else {
				outOfRange++;
			}
		}

		int excluded() {
			return closed + noCoordinates + outOfRange;
		}
	}

}
