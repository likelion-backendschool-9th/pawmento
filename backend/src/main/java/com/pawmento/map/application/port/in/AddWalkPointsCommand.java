package com.pawmento.map.application.port.in;

import java.time.Instant;
import java.util.List;

/**
 * 산책에 쌓을 좌표 묶음.
 *
 * @param points 이번 요청의 좌표. 비었거나 100개를 넘으면 도메인이 거절한다
 */
public record AddWalkPointsCommand(Long memberId, Long walkId, List<Point> points) {

	// 기기에서 좌표를 얻은 시각(recordedAt)과 위치
	public record Point(double latitude, double longitude, Instant recordedAt) {
	}

}
