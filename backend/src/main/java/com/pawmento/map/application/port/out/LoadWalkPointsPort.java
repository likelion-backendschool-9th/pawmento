package com.pawmento.map.application.port.out;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.pawmento.map.domain.WalkPoint;

public interface LoadWalkPointsPort {

	// 산책의 좌표를 기록 시각 순으로 돌려준다
	List<WalkPoint> loadByWalkId(Long walkId);

	/**
	 * 여러 산책의 좌표를 한 번에 읽어 산책 id별로 기록 시각 순서로 돌려준다. 좌표가 없는 산책은 빈 목록이다.
	 * 산책마다 따로 읽지 않는 것이 목적이다.
	 */
	Map<Long, List<WalkPoint>> loadByWalkIds(Collection<Long> walkIds);

	// 산책에 쌓인 좌표의 개수
	long countByWalkId(Long walkId);

}
