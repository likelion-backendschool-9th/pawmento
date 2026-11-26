package com.pawmento.map.application.port.in;

import java.math.BigDecimal;
import java.util.Set;

/**
 * 장소 이름·주소 검색 요청(#195-R1). 장소는 모든 회원이 같이 보므로 회원 정보는 담지 않는다.
 * 입력 포트는 도메인 타입을 노출하지 않으므로 종류는 문자열로 받는다.
 *
 * @param query 사용자가 입력한 검색어(정규화하기 전). 길이는 서비스가 정규화한 뒤에 검사한다
 * @param categories 요청한 종류의 이름({@link ListPlacesQuery#CATEGORIES} 중에서). 보내지 않았을 때 모든 종류로 채우는 일은 웹 어댑터가 한다
 * @param latitude 지도 중심의 위도. {@code longitude}와 함께 있거나 함께 없어야 한다(웹 어댑터가 검사한다)
 * @param longitude 지도 중심의 경도
 */
public record SearchPlacesQuery(String query, Set<String> categories, BigDecimal latitude, BigDecimal longitude) {
}
