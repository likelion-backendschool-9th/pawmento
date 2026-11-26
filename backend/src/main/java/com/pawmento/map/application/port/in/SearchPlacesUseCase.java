package com.pawmento.map.application.port.in;

public interface SearchPlacesUseCase {

	/**
	 * 이름이나 주소에 검색어가 든 병원·미용·보호센터를 찾는다. 검색어가 정규화한 뒤 2자 미만이거나 30자를 넘으면 규칙 위반으로 거절한다(400).
	 */
	SearchPlacesResult search(SearchPlacesQuery query);

}
