package com.pawmento.map.adapter.in.web;

import java.time.Instant;
import java.util.List;

import com.pawmento.map.application.port.in.GetCurrentWalkResult;

/**
 * 지금 진행 중인 산책 응답. 진행 중인 산책은 끝내지 않았으므로 endedAt은 항상 null이고 status는 IN_PROGRESS다.
 * 첫 좌표를 받기 전이면 points는 빈 목록이다.
 */
record CurrentWalkResponse(Long walkId, Instant startedAt, Instant endedAt, String status,
		List<WalksResponse.Point> points) {

	static CurrentWalkResponse from(GetCurrentWalkResult result) {
		return new CurrentWalkResponse(result.walkId(), result.startedAt(), result.endedAt(), result.status(),
				result.points()
					.stream()
					.map(point -> new WalksResponse.Point(point.latitude(), point.longitude(), point.recordedAt()))
					.toList());
	}

}
