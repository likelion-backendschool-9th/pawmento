package com.pawmento.map.application.port.in;

import java.math.BigDecimal;
import java.util.Set;

import com.pawmento.map.domain.PlaceCategory;

/**
 * 지도 영역 안의 장소를 찾는 요청. 장소는 모든 회원이 같이 보므로 회원 정보는 담지 않는다.
 *
 * @param categories 요청한 종류. 비어 있으면 안 되고, 보내지 않았을 때 모든 종류로 채우는 일은 웹 어댑터가 한다
 */
public record ListPlacesQuery(BigDecimal minLatitude, BigDecimal minLongitude, BigDecimal maxLatitude,
		BigDecimal maxLongitude, Set<PlaceCategory> categories) {
}
