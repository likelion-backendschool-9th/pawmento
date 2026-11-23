package com.pawmento.map.domain;

/**
 * 정보 지도의 장소 하나(병원·미용·보호센터).
 * 없는 값은 null이다. 특히 closedDays가 null이면 "휴무 정보 없음"이지 "연중무휴"가 아니다.
 */
public record Place(Long id, PlaceCategory category, String name, String address, String phone, double latitude,
		double longitude, boolean open24h, Open24hBasis open24hBasis, String openingHours, String closedDays) {
}
