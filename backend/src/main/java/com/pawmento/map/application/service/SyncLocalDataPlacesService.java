package com.pawmento.map.application.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.pawmento.map.application.exception.LocalDataSyncFailedException;
import com.pawmento.map.application.exception.LocalDataSyncFailedException.Reason;
import com.pawmento.map.application.port.in.SyncLocalDataPlacesCommand;
import com.pawmento.map.application.port.in.SyncLocalDataPlacesResult;
import com.pawmento.map.application.port.in.SyncLocalDataPlacesUseCase;
import com.pawmento.map.application.port.out.AppliedPlaces;
import com.pawmento.map.application.port.out.LoadLocalDataPort;
import com.pawmento.map.application.port.out.LocalDataSnapshot;
import com.pawmento.map.application.port.out.StorePlacesPort;
import com.pawmento.map.domain.LocalDataExclusion;
import com.pawmento.map.domain.LocalDataPlaceFactory;
import com.pawmento.map.domain.LocalDataRecord;
import com.pawmento.map.domain.PlaceCategory;
import com.pawmento.map.domain.PlaceDraft;

import lombok.RequiredArgsConstructor;

/**
 * 공공데이터(병원·미용)를 전부 받아 장소를 통째로 맞춘다. 받는 데 몇 분이 걸려서 받기(네트워크)는 트랜잭션 밖에서 하고, 믿을 수
 * 있는 전체 스냅샷일 때만 저장소 포트가 한 트랜잭션으로 반영한다(#172-S4, S5, S20).
 */
// 아직 @Service로 등록하지 않는다. 공공데이터를 받아오는 어댑터(LoadLocalDataPort 구현)를 다음 PR에서 만들고, 그때 등록해 연결한다.
// 지금 등록하면 구현이 없는 포트 때문에 앱이 시작하지 못한다
@RequiredArgsConstructor
class SyncLocalDataPlacesService implements SyncLocalDataPlacesUseCase {

	private final LoadLocalDataPort loadLocalDataPort;

	private final StorePlacesPort storePlacesPort;

	@Override
	public SyncLocalDataPlacesResult sync(SyncLocalDataPlacesCommand command) {
		PlaceCategory category = toCategory(command.category());
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

		List<PlaceDraft> drafts = new ArrayList<>();
		int closed = 0;
		int noCoordinates = 0;
		int outOfRange = 0;
		for (LocalDataRecord record : unique.values()) {
			LocalDataPlaceFactory.Outcome outcome = LocalDataPlaceFactory.create(category, record);
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

		// 거른 뒤 저장할 건수가 기존의 절반 미만이면 불완전한 수집일 수 있어 지도를 비우지 않는다
		int existing = storePlacesPort.countByCategory(category);
		if (existing > 0 && drafts.size() * 2 < existing) {
			throw LocalDataSyncFailedException.rejected(Reason.TOO_FEW_PLACES);
		}

		AppliedPlaces applied = storePlacesPort.replace(category, drafts);
		return new SyncLocalDataPlacesResult(category.name(), unique.size(), drafts.size(),
				closed + noCoordinates + outOfRange, closed, noCoordinates, outOfRange, applied.inserted(),
				applied.updated(), applied.deleted());
	}

	// 병원과 미용만 이 API로 가져온다. 웹 어댑터가 이름을 검사하고 오므로 그 밖의 값은 호출한 쪽의 버그다
	private static PlaceCategory toCategory(String name) {
		if ("HOSPITAL".equals(name)) {
			return PlaceCategory.HOSPITAL;
		}
		if ("GROOMING".equals(name)) {
			return PlaceCategory.GROOMING;
		}
		throw new IllegalArgumentException("가져올 수 없는 종류입니다: " + name);
	}

}
