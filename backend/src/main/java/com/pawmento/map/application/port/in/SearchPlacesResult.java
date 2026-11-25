package com.pawmento.map.application.port.in;

import java.time.Instant;
import java.util.List;

/**
 * 장소 검색 결과.
 *
 * @param places 지도 중심을 받았으면 가까운 순, 아니면 이름이 검색어로 시작하는 곳 → 이름에 든 곳 → 주소에만 든 곳 순. 최대 20곳
 * @param truncated 겹침을 지운 뒤에도 20곳보다 많아서 잘렸으면 true
 * @param dataAsOf 이번 결과에 든 장소의 종류(없으면 요청한 종류) 중 가장 오래된 "마지막으로 가져온 시각". 모르면 null(#172-S21)
 */
public record SearchPlacesResult(List<Item> places, boolean truncated, Instant dataAsOf) {

	/**
	 * 장소 하나. 영역 조회의 장소와 같은 필드에 지도 중심까지의 거리를 더했다.
	 *
	 * @param distanceMeters 지도 중심에서의 직선거리(m, 반올림한 정수). 지도 중심을 받지 않았으면 null
	 */
	public record Item(Long id, String category, String name, String address, String phone, double latitude,
			double longitude, boolean open24h, String open24hBasis, String openingHours, String closedDays,
			Integer distanceMeters) {
	}

}
