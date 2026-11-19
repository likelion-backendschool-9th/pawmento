package com.pawmento.map.adapter.in.web;

import java.time.Instant;
import java.util.List;

import com.pawmento.map.application.port.in.ListWalksResult;

/**
 * 날짜별 산책 응답. endedAt은 보호자가 끝낸 산책만 값이 있고, 끝내지 않은 산책(진행 중이거나 중단됨)은 null이다.
 */
record WalksResponse(List<Walk> walks) {

	record Walk(Long walkId, Instant startedAt, Instant endedAt, String status, List<Point> points) {
	}

	record Point(double latitude, double longitude, Instant recordedAt) {
	}

	static WalksResponse from(ListWalksResult result) {
		List<Walk> walks = result.walks()
			.stream()
			.map(walk -> new Walk(walk.walkId(), walk.startedAt(), walk.endedAt(), walk.status(),
					walk.points()
						.stream()
						.map(point -> new Point(point.latitude(), point.longitude(), point.recordedAt()))
						.toList()))
			.toList();
		return new WalksResponse(walks);
	}

}
