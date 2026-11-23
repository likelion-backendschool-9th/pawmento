package com.pawmento.map.application.port.in;

import java.time.Instant;
import java.util.List;

/**
 * 지금 진행 중인 산책 하나와 그 경로. 좌표는 기록 시각 순서다.
 *
 * @param endedAt 진행 중인 산책은 끝내지 않았으므로 항상 비어 있다(null)
 * @param status 진행 중인 산책이라 항상 IN_PROGRESS다
 * @param points 서버에 쌓인 좌표. 첫 좌표를 받기 전이면 빈 목록이다
 */
public record GetCurrentWalkResult(Long walkId, Instant startedAt, Instant endedAt, String status,
		List<Point> points) {

	public record Point(double latitude, double longitude, Instant recordedAt) {
	}

}
