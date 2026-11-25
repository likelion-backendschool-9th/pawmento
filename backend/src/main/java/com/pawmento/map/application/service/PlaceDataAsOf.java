package com.pawmento.map.application.service;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.pawmento.map.application.port.out.LoadPlaceDataAsOfPort;
import com.pawmento.map.domain.Place;
import com.pawmento.map.domain.PlaceCategory;

/**
 * 응답의 "데이터 기준 시각"을 정하는 규칙. 장소 목록(#172-S21)과 장소 검색(#195-S1)이 같은 규칙을 쓴다.
 */
final class PlaceDataAsOf {

	private PlaceDataAsOf() {
	}

	/**
	 * 이번 응답에 든 장소의 종류(장소가 하나도 없으면 요청한 종류)의 가져온 시각 중 가장 오래된 시각. 그중 가져온 기록을 모르는
	 * 종류가 있으면 null이다. 배포 직후처럼 기준 시각을 모르는 이전 데이터가 섞이면 다른 종류의 새 시각을 대표로 보여 주지 않아야
	 * 데이터가 실제보다 새것처럼 보이지 않는다.
	 */
	static Instant of(LoadPlaceDataAsOfPort port, List<Place> places, Set<PlaceCategory> requested) {
		Set<PlaceCategory> relevant = EnumSet.noneOf(PlaceCategory.class);
		places.forEach(place -> relevant.add(place.category()));
		if (relevant.isEmpty()) {
			relevant.addAll(requested);
		}
		Map<PlaceCategory, Instant> synced = port.syncedAt(relevant);
		if (!synced.keySet().containsAll(relevant)) {
			return null;
		}
		return Collections.min(synced.values());
	}

}
