package com.pawmento.map.application.port.out;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import com.pawmento.map.domain.PlaceCategory;

public interface LoadPlaceDataAsOfPort {

	/**
	 * 요청한 종류 중 가장 오래된 "마지막으로 가져온 시각". 한 종류라도 오래됐으면 그 시각을 알려서 데이터가 실제보다 새것처럼
	 * 보이지 않게 한다. 가져온 적이 있는 종류가 없으면 비어 있다(#172-S21).
	 */
	Optional<Instant> oldestSyncedAt(Set<PlaceCategory> categories);

}
