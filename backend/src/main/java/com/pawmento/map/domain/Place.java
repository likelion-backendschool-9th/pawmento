package com.pawmento.map.domain;

/**
 * 정보 지도의 장소 하나(병원·미용·보호센터).
 * 없는 값은 null이다. 특히 closedDays가 null이면 "휴무 정보 없음"이지 "연중무휴"가 아니다.
 */
public record Place(Long id, PlaceCategory category, String name, String address, String phone, double latitude,
		double longitude, boolean open24h, Open24hBasis open24hBasis, String openingHours, String closedDays) {

	/** 검색용으로 정규화한 이름(#195). 영역 조회의 겹침 제거와 같은 기준이다. */
	public String nameKey() {
		return PlaceSelection.comparableName(name);
	}

	/** 검색용으로 정규화한 주소. 주소가 없으면 null이다. */
	public String addressKey() {
		return address == null ? null : PlaceSelection.comparableName(address);
	}

}
