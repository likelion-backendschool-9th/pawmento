package com.pawmento.map.application.port.in;

/**
 * 지도 영역 안의 핀을 찾는 요청.
 *
 * @param memberId 요청한 회원. 이 회원이 만든 기록의 위치만 찾는다
 * @param petId 반려동물 하나
 */
public record ListMapPinsQuery(Long memberId, Long petId, double minLatitude, double minLongitude,
		double maxLatitude, double maxLongitude) {
}
