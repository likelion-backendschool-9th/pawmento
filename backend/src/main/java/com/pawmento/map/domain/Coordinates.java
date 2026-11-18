package com.pawmento.map.domain;

import com.pawmento.map.domain.exception.InvalidCoordinatesException;

// 위도·경도의 허용 범위. 같은 규칙을 RecordLocation과 MapArea가 함께 쓴다
final class Coordinates {

	private static final double MAX_LATITUDE = 90.0;
	private static final double MAX_LONGITUDE = 180.0;

	private Coordinates() {
	}

	static void requireLatitude(double latitude) {
		if (!Double.isFinite(latitude) || Math.abs(latitude) > MAX_LATITUDE) {
			throw InvalidCoordinatesException.latitudeOutOfRange();
		}
	}

	static void requireLongitude(double longitude) {
		if (!Double.isFinite(longitude) || Math.abs(longitude) > MAX_LONGITUDE) {
			throw InvalidCoordinatesException.longitudeOutOfRange();
		}
	}

}
