package com.pawmento.map.application.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pawmento.map.application.exception.WalkNotFoundException;
import com.pawmento.map.application.port.in.AddWalkPointsCommand;
import com.pawmento.map.application.port.in.AddWalkPointsUseCase;
import com.pawmento.map.application.port.out.LoadWalkPort;
import com.pawmento.map.application.port.out.LoadWalkPointsPort;
import com.pawmento.map.application.port.out.SaveWalkPointsPort;
import com.pawmento.map.application.port.out.SaveWalkPort;
import com.pawmento.map.domain.Walk;
import com.pawmento.map.domain.WalkPoint;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
class AddWalkPointsService implements AddWalkPointsUseCase {

	private final LoadWalkPort loadWalkPort;
	private final LoadWalkPointsPort loadWalkPointsPort;
	private final SaveWalkPointsPort saveWalkPointsPort;
	private final SaveWalkPort saveWalkPort;
	private final Clock clock;

	/**
	 * 순서가 중요하다. 한도를 넘는 요청은 예외로 트랜잭션을 되돌려 이미 넣은 좌표까지 없어지게 한다.
	 * 1) 행을 잠그고 읽는다(같은 산책의 쌓기·끝내기가 겹쳐도 개수 한도와 끝낸 시각을 지킨다)
	 * 2) 소유자·묶음 크기·상태를 본다 3) 좌표를 도메인 값으로 바꾸며 범위를 검사한다
	 * 4) 새로 쌓인 개수로 한도를 본다 5) 통과했을 때만 마지막 활동 시각을 갱신해 저장한다
	 */
	@Override
	@Transactional
	public void add(AddWalkPointsCommand command) {
		Walk walk = loadWalkPort.loadByIdForUpdate(command.walkId()).orElseThrow(WalkNotFoundException::new);
		// DB가 마이크로초까지 저장하므로 처음부터 맞춘다. 같은 좌표를 다시 보내도 같은 값으로 비교된다
		Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
		walk.requireAcceptingPoints(command.memberId(), now, command.points().size());

		List<WalkPoint> points = command.points()
			.stream()
			.map(point -> WalkPoint.of(walk.getId(), point.latitude(), point.longitude(),
					point.recordedAt().truncatedTo(ChronoUnit.MICROS)))
			.toList();
		long before = loadWalkPointsPort.countByWalkId(walk.getId());
		int inserted = saveWalkPointsPort.insertIfAbsent(points);
		walk.requireCapacity(before, inserted);

		walk.markPointsReceived(now);
		saveWalkPort.save(walk);
	}

}
