package com.pawmento.map.application.port.out;

import java.util.List;

import com.pawmento.map.domain.WalkPoint;

public interface SaveWalkPointsPort {

	/**
	 * 좌표를 쌓는다. 같은 산책에서 기록 시각이 같은 좌표가 이미 있으면 새로 쌓지 않고 기존 값을 그대로 둔다(재전송).
	 *
	 * @return 새로 쌓인 좌표의 개수
	 */
	int insertIfAbsent(List<WalkPoint> points);

}
