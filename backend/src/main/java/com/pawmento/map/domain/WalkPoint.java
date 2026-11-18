package com.pawmento.map.domain;

import java.time.Instant;

import lombok.Getter;

/**
 * 산책 중에 쌓이는 좌표 하나. 같은 산책에서 같은 recordedAt이 다시 오면 같은 좌표로 본다(재전송).
 */
@Getter
public class WalkPoint {

	private final Long walkId;
	private final double latitude;
	private final double longitude;

	// 기기에서 좌표를 얻은 시각
	private final Instant recordedAt;

	private WalkPoint(Long walkId, double latitude, double longitude, Instant recordedAt) {
		if (walkId == null || recordedAt == null) {
			throw new IllegalArgumentException("산책과 기록 시각이 필요합니다.");
		}
		Coordinates.requireLatitude(latitude);
		Coordinates.requireLongitude(longitude);
		this.walkId = walkId;
		this.latitude = latitude;
		this.longitude = longitude;
		this.recordedAt = recordedAt;
	}

	public static WalkPoint of(Long walkId, double latitude, double longitude, Instant recordedAt) {
		return new WalkPoint(walkId, latitude, longitude, recordedAt);
	}

}
