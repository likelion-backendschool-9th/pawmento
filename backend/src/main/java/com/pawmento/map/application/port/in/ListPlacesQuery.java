package com.pawmento.map.application.port.in;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * 지도 영역 안의 장소를 찾는 요청. 장소는 모든 회원이 같이 보므로 회원 정보는 담지 않는다.
 * 입력 포트는 도메인 타입을 노출하지 않으므로 종류는 문자열로 받고, 서비스가 도메인 종류로 바꾼다.
 *
 * @param categories 요청한 종류의 이름({@link #CATEGORIES} 중에서). 비어 있으면 안 되고, 보내지 않았을 때 모든 종류로
 *            채우는 일은 웹 어댑터가 한다
 */
public record ListPlacesQuery(BigDecimal minLatitude, BigDecimal minLongitude, BigDecimal maxLatitude,
		BigDecimal maxLongitude, Set<String> categories) {

	/** 받을 수 있는 장소 종류의 이름. 웹 어댑터가 요청을 검사할 때 쓴다. */
	public static final List<String> CATEGORIES = List.of("HOSPITAL", "GROOMING", "SHELTER");

}
