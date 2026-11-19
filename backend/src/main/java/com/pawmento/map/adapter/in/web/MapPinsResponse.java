package com.pawmento.map.adapter.in.web;

import java.util.List;

import com.pawmento.map.application.port.in.ListMapPinsResult;

/**
 * 핀 목록 응답. 핀에는 petId를 담지 않는다. 요청한 petId가 곧 그 핀의 반려동물이다.
 *
 * @param truncated 영역 안의 핀이 최대 개수(500)를 넘어 잘렸으면 true
 */
record MapPinsResponse(List<Pin> pins, boolean truncated) {

	record Pin(Long diaryId, double latitude, double longitude, String placeName) {
	}

	static MapPinsResponse from(ListMapPinsResult result) {
		List<Pin> pins = result.pins()
			.stream()
			.map(pin -> new Pin(pin.diaryId(), pin.latitude(), pin.longitude(), pin.placeName()))
			.toList();
		return new MapPinsResponse(pins, result.truncated());
	}

}
