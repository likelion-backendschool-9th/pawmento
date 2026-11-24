package com.pawmento.map.application.port.out;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import com.pawmento.map.domain.PlaceCategory;

public interface LoadPlaceDataAsOfPort {

	/**
	 * 요청한 종류 중 마지막으로 가져온 시각을 아는 종류의 시각. 가져온 기록이 없는 종류(배포 직후 이전 데이터 등)는 키가 없다.
	 * 어느 시각을 보여 줄지는 호출한 쪽이 정한다(#172-S21).
	 */
	Map<PlaceCategory, Instant> syncedAt(Set<PlaceCategory> categories);

}
