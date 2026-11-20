package com.pawmento.map.application.port.in;

import java.math.BigDecimal;

/**
 * 지도 영역 안의 핀을 찾는 요청.
 *
 * @param memberId 요청한 회원. 이 회원이 만든 기록의 위치만 찾는다
 * @param petId 반려동물 하나
 * @param minLatitude 영역의 값. 정밀도를 지키려고 십진수로 받고, 순서 검사 뒤에 조회용 double로 바꾼다
 */
public record ListMapPinsQuery(Long memberId, Long petId, BigDecimal minLatitude, BigDecimal minLongitude,
		BigDecimal maxLatitude, BigDecimal maxLongitude) {
}
