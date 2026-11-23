package com.pawmento.map.application.port.in;

import java.util.List;

/**
 * 영역 안의 장소 목록.
 *
 * @param places 영역 가운데에 가까운 순으로, 최대 500곳
 * @param truncated 겹침을 지운 뒤에도 500곳보다 많아서 잘렸으면 true
 */
public record ListPlacesResult(List<Item> places, boolean truncated) {

	/**
	 * 장소 하나. 없는 값은 null이다. closedDays가 null이면 "휴무 정보 없음"이다.
	 *
	 * @param category HOSPITAL, GROOMING, SHELTER 중 하나
	 * @param open24hBasis 24시간으로 본 근거. NAME(이름 기준 추정), HOURS(제공된 운영 시간), 아니면 null
	 */
	public record Item(Long id, String category, String name, String address, String phone, double latitude,
			double longitude, boolean open24h, String open24hBasis, String openingHours, String closedDays) {
	}

}
