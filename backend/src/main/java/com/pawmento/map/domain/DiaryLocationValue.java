package com.pawmento.map.domain;

/** 지도 입력과 저장 상태가 함께 쓰는 좌표·장소 이름 값. */
public record DiaryLocationValue(double latitude, double longitude, String placeName) {
	public DiaryLocationValue {
		Coordinates.requireLatitude(latitude);
		Coordinates.requireLongitude(longitude);
		latitude = latitude == 0 ? 0 : latitude;
		longitude = longitude == 0 ? 0 : longitude;
		placeName = DiaryLocation.normalizePlaceName(placeName);
	}
}
