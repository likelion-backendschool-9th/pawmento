package com.pawmento.map.application.port.out;

import java.util.List;

import com.pawmento.map.domain.WalkPoint;

public interface LoadWalkPointsPort {

	// 산책의 좌표를 기록 시각 순으로 돌려준다
	List<WalkPoint> loadByWalkId(Long walkId);

	// 산책에 쌓인 좌표의 개수
	long countByWalkId(Long walkId);

}
