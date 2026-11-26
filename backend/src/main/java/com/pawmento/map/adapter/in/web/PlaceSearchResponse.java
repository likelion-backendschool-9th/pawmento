package com.pawmento.map.adapter.in.web;

import java.time.Instant;
import java.util.List;

import com.pawmento.map.application.port.in.SearchPlacesResult;

/**
 * 장소 검색 응답(#195). 장소는 영역 조회(PlacesResponse)와 같은 필드에 지도 중심까지의 거리를 더한 것이다.
 * 없는 값은 필드를 빼지 않고 null로 보낸다.
 *
 * @param truncated 겹침을 지운 뒤에도 최대 개수(20)보다 많아서 잘렸으면 true
 * @param dataAsOf 이번 결과의 종류 중 가장 오래된 "마지막으로 가져온 시각". 모르면 null(#172-S21)
 */
record PlaceSearchResponse(List<Place> places, boolean truncated, Instant dataAsOf) {

	/** @param distanceMeters 지도 중심에서의 직선거리(m). 지도 중심을 보내지 않았으면 null */
	record Place(Long id, String category, String name, String address, String phone, double latitude,
			double longitude, boolean open24h, String open24hBasis, String openingHours, String closedDays,
			Integer distanceMeters) {
	}

	static PlaceSearchResponse from(SearchPlacesResult result) {
		List<Place> places = result.places()
			.stream()
			.map(place -> new Place(place.id(), place.category(), place.name(), place.address(), place.phone(),
					place.latitude(), place.longitude(), place.open24h(), place.open24hBasis(), place.openingHours(),
					place.closedDays(), place.distanceMeters()))
			.toList();
		return new PlaceSearchResponse(places, result.truncated(), result.dataAsOf());
	}

}
