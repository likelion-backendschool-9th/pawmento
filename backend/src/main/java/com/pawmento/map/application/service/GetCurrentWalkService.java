package com.pawmento.map.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.pawmento.map.application.port.in.GetCurrentWalkQuery;
import com.pawmento.map.application.port.in.GetCurrentWalkResult;
import com.pawmento.map.application.port.in.GetCurrentWalkUseCase;
import com.pawmento.map.application.port.out.LoadWalkPointsPort;
import com.pawmento.map.application.port.out.LoadWalkPort;
import com.pawmento.map.domain.Walk;
import com.pawmento.map.domain.WalkPoint;
import com.pawmento.map.domain.WalkStatus;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
class GetCurrentWalkService implements GetCurrentWalkUseCase {

	private final LoadWalkPort loadWalkPort;
	private final LoadWalkPointsPort loadWalkPointsPort;
	private final Clock clock;

	/**
	 * 만든 회원과 반려동물로 거르므로 pet의 소유 확인을 부르지 않는다. 다른 회원의 반려동물은 비어 있다.
	 * 진행 중인 산책을 시작한 날짜와 상관없이 찾는다. 서버는 진행 중 여부를 시작 날짜가 아니라 마지막 활동 시각으로 판정하므로,
	 * 며칠 전에 시작해도 좌표를 계속 받는 산책은 진행 중이고 날짜별 조회에는 시작한 날에만 나온다.
	 *
	 * 산책 행과 좌표를 같은 읽기 스냅샷(REPEATABLE READ)에서 읽는다. 기본 격리 수준에서는 두 조회 사이에 좌표 추가가 커밋되면
	 * 갱신 전 마지막 활동 시각과 추가된 좌표를 함께 읽을 수 있다. 이미 열린 트랜잭션 안에서 부르면 격리 수준 지정이
	 * 무시되므로 새 읽기 트랜잭션을 시작한다(날짜별 조회와 같다).
	 */
	@Override
	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, propagation = Propagation.REQUIRES_NEW)
	public Optional<GetCurrentWalkResult> get(GetCurrentWalkQuery query) {
		// 진행 중 판정은 지금 시각 기준이라 한 요청 안에서는 같은 시각으로 계산한다
		Instant now = clock.instant();
		Optional<Walk> found = loadWalkPort.loadOpenWalkActiveSince(query.memberId(), query.petId(),
				now.minus(Walk.INTERRUPTED_AFTER));
		// 조회 조건은 Walk#status와 같은 기준이지만, 상태 판단은 도메인이 하므로 한 번 더 확인한다
		if (found.isEmpty() || found.get().status(now) != WalkStatus.IN_PROGRESS) {
			return Optional.empty();
		}
		Walk walk = found.get();
		List<WalkPoint> points = loadWalkPointsPort.loadByWalkIds(List.of(walk.getId()))
			.getOrDefault(walk.getId(), List.of());
		return Optional.of(new GetCurrentWalkResult(walk.getId(), walk.getStartedAt(), walk.getEndedAt(),
				WalkStatus.IN_PROGRESS.name(), points.stream()
					.map(point -> new GetCurrentWalkResult.Point(point.getLatitude(), point.getLongitude(),
							point.getRecordedAt()))
					.toList()));
	}

}
