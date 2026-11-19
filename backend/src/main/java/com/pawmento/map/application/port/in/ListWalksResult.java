package com.pawmento.map.application.port.in;

import java.time.Instant;
import java.util.List;

/**
 * 하루 동안 시작한 산책들. 산책은 시작한 시각 순서이고, 각 산책의 좌표는 기록 시각 순서다.
 *
 * @param walks 그 날 시작한 산책. 없으면 빈 목록이다
 */
public record ListWalksResult(List<Walk> walks) {

	/**
	 * 산책 하나와 그 경로.
	 *
	 * @param endedAt 보호자가 끝낸 시각. 끝내지 않은 산책(진행 중이거나 중단됨)은 비어 있다(null)
	 */
	public record Walk(Long walkId, Instant startedAt, Instant endedAt, String status, List<Point> points) {
	}

	public record Point(double latitude, double longitude, Instant recordedAt) {
	}

}
