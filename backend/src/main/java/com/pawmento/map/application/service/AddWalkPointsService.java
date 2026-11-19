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
import com.pawmento.map.application.port.out.LockPetWalksPort;
import com.pawmento.map.application.port.out.SaveWalkPointsPort;
import com.pawmento.map.application.port.out.SaveWalkPort;
import com.pawmento.map.domain.Walk;
import com.pawmento.map.domain.WalkPoint;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
class AddWalkPointsService implements AddWalkPointsUseCase {

	private final LoadWalkPort loadWalkPort;
	private final LockPetWalksPort lockPetWalksPort;
	private final LoadWalkPointsPort loadWalkPointsPort;
	private final SaveWalkPointsPort saveWalkPointsPort;
	private final SaveWalkPort saveWalkPort;
	private final Clock clock;

	/**
	 * 순서가 중요하다. 한도를 넘는 요청은 예외로 트랜잭션을 되돌려 이미 넣은 좌표까지 없어지게 한다.
	 * 1) 산책을 잠그지 않고 읽어 소유자를 확인한다(남의 산책으로 다른 반려동물의 잠금을 잡지 못하게 한다)
	 * 2) 반려동물 잠금 → 산책 행 잠금 순서로 잠근다. 반려동물 잠금은 산책 시작과 같은 것이라, 활동 시각을 갱신하는 요청과
	 *    "진행 중인 산책이 있는가"를 보는 시작 요청이 겹쳐도 같은 반려동물의 진행 중인 산책이 둘이 되지 않는다.
	 *    반려동물 잠금을 먼저 잡는 순서를 지켜야 교착이 없다(끝내기는 산책 행 잠금만 잡는다)
	 * 3) 잠금을 얻은 뒤의 시각으로 묶음 크기·상태를 본다 4) 좌표를 도메인 값으로 바꾸며 범위를 검사한다
	 * 5) 새로 쌓인 개수로 한도를 본다 6) 통과했을 때만 마지막 활동 시각을 갱신해 저장한다
	 */
	@Override
	@Transactional
	public void add(AddWalkPointsCommand command) {
		Walk found = loadWalkPort.loadById(command.walkId()).orElseThrow(WalkNotFoundException::new);
		found.requireOwner(command.memberId());
		lockPetWalksPort.lock(found.getPetId());
		Walk walk = loadWalkPort.loadByIdForUpdate(command.walkId()).orElseThrow(WalkNotFoundException::new);
		// 잠금을 기다린 시간까지 반영하려고 잠금을 얻은 뒤에 읽는다.
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
