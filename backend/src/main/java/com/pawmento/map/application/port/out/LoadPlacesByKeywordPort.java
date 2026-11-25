package com.pawmento.map.application.port.out;

import java.util.List;
import java.util.Set;

import com.pawmento.map.domain.PlaceCategory;
import com.pawmento.map.domain.Place;

public interface LoadPlacesByKeywordPort {

	/**
	 * 정규화한 이름이나 정규화한 주소에 {@code key}가 이어서 들어 있는 요청한 종류의 장소를 모두 돌려준다. 순서는 정하지 않는다.
	 * {@code key}는 글자 그대로 찾는다. 검색 규칙의 특수문자(%, _ 등)를 해석하지 않는다.
	 */
	List<Place> loadByKeyword(String key, Set<PlaceCategory> categories);

}
