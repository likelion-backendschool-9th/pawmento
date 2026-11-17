package com.pawmento.map.application.port.in;

import java.util.List;

/**
 * 영역 안의 핀 목록.
 *
 * @param pins 오래된 기록 순으로, 최대 500개
 * @param truncated 영역 안에 500개보다 많아서 잘렸으면 true
 */
public record ListMapPinsResult(List<Pin> pins, boolean truncated) {

	public record Pin(Long recordId, double latitude, double longitude, String placeName) {
	}

}
