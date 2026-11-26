package com.pawmento.map.domain;

import java.time.Instant;

/**
 * 저장하기 전의 장소. 원본 관리번호로 같은 장소를 찾아 갱신하는 데 쓴다. id는 저장할 때 정해지므로 없다.
 * 병원·미용 원본에는 운영 시간과 휴무 정보가 없어 openingHours와 closedDays는 null(정보 없음)이다.
 *
 * @param sourceUpdatedAt 원본이 마지막으로 바뀐 시각. 읽을 수 없으면 null
 */
public record PlaceDraft(PlaceCategory category, String sourceId, String name, String address, String phone,
		double latitude, double longitude, boolean open24h, Open24hBasis open24hBasis, String openingHours,
		String closedDays, Instant sourceUpdatedAt) {

	/** 검색용으로 정규화한 이름(#195). 장소를 저장할 때 함께 남긴다. */
	public String nameKey() {
		return PlaceSelection.comparableName(name);
	}

	/** 검색용으로 정규화한 주소. 주소가 없으면 null이다. */
	public String addressKey() {
		return address == null ? null : PlaceSelection.comparableName(address);
	}

}
