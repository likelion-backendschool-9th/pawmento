package com.pawmento.map.adapter.in.web;

import java.time.Instant;
import java.util.List;

import com.pawmento.map.application.port.in.ListPlacesResult;

/**
 * 장소 목록 응답. 없는 값은 필드를 빼지 않고 null로 보낸다(정보 없음).
 *
 * @param truncated 영역 안의 장소가 최대 개수(500)를 넘어 잘렸으면 true
 * @param dataAsOf 요청한 종류 중 가장 오래된 "마지막으로 가져온 시각". 가져온 적이 없으면 null(#172-S21)
 */
record PlacesResponse(List<Place> places, boolean truncated, Instant dataAsOf) {

	record Place(Long id, String category, String name, String address, String phone, double latitude,
			double longitude, boolean open24h, String open24hBasis, String openingHours, String closedDays) {
	}

	static PlacesResponse from(ListPlacesResult result) {
		List<Place> places = result.places()
			.stream()
			.map(place -> new Place(place.id(), place.category(), place.name(), place.address(), place.phone(),
					place.latitude(), place.longitude(), place.open24h(), place.open24hBasis(), place.openingHours(),
					place.closedDays()))
			.toList();
		return new PlacesResponse(places, result.truncated(), result.dataAsOf());
	}

}
