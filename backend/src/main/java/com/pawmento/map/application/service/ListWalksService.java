package com.pawmento.map.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.pawmento.map.application.port.in.ListWalksQuery;
import com.pawmento.map.application.port.in.ListWalksResult;
import com.pawmento.map.application.port.in.ListWalksUseCase;
import com.pawmento.map.application.port.out.LoadWalkPointsPort;
import com.pawmento.map.application.port.out.LoadWalkPort;
import com.pawmento.map.domain.Walk;
import com.pawmento.map.domain.WalkDay;
import com.pawmento.map.domain.WalkPoint;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
class ListWalksService implements ListWalksUseCase {

	private final LoadWalkPort loadWalkPort;
	private final LoadWalkPointsPort loadWalkPointsPort;
	private final Clock clock;

	/**
	 * 만든 회원과 반려동물로 거르므로 pet의 소유 확인을 부르지 않는다. 다른 회원의 반려동물은 빈 목록이다.
	 * 좌표는 산책마다 읽지 않고 한 번에 읽는다.
	 *
	 * 산책 행과 좌표를 같은 읽기 스냅샷(REPEATABLE READ)에서 읽는다. 기본 격리 수준에서는 두 조회 사이에 좌표 추가가 커밋되면
	 * 갱신 전 마지막 활동 시각과 추가된 좌표를 함께 읽어, 새 좌표를 담고도 INTERRUPTED를 돌려줄 수 있다.
	 * 이미 열린 트랜잭션 안에서 부르면 격리 수준 지정이 무시되므로 새 읽기 트랜잭션을 시작한다.
	 */
	@Override
	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, propagation = Propagation.REQUIRES_NEW)
	public ListWalksResult list(ListWalksQuery query) {
		WalkDay day = new WalkDay(query.date());
		List<Walk> walks = loadWalkPort.loadStartedBetween(query.memberId(), query.petId(), day.startInclusive(),
				day.endExclusive());
		if (walks.isEmpty()) {
			return new ListWalksResult(List.of());
		}
		Map<Long, List<WalkPoint>> pointsByWalk = loadWalkPointsPort
			.loadByWalkIds(walks.stream().map(Walk::getId).toList());
		// 중단 판정은 지금 시각 기준이라 한 요청 안에서는 같은 시각으로 계산한다
		Instant now = clock.instant();
		List<ListWalksResult.Walk> result = walks.stream()
			.map(walk -> new ListWalksResult.Walk(walk.getId(), walk.getStartedAt(), walk.getEndedAt(),
					walk.status(now).name(),
					pointsByWalk.getOrDefault(walk.getId(), List.of())
						.stream()
						.map(point -> new ListWalksResult.Point(point.getLatitude(), point.getLongitude(),
								point.getRecordedAt()))
						.toList()))
			.toList();
		return new ListWalksResult(result);
	}

}
