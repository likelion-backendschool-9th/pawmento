package com.pawmento.map.application.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pawmento.map.application.exception.WalkAlreadyInProgressException;
import com.pawmento.map.application.port.in.StartWalkCommand;
import com.pawmento.map.application.port.in.StartWalkResult;
import com.pawmento.map.application.port.in.StartWalkUseCase;
import com.pawmento.map.application.port.out.LoadWalkPort;
import com.pawmento.map.application.port.out.LockPetWalksPort;
import com.pawmento.map.application.port.out.SaveWalkPort;
import com.pawmento.map.application.port.out.VerifyPetAccessPort;
import com.pawmento.map.domain.Walk;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
class StartWalkService implements StartWalkUseCase {

	private final VerifyPetAccessPort verifyPetAccessPort;
	private final LockPetWalksPort lockPetWalksPort;
	private final LoadWalkPort loadWalkPort;
	private final SaveWalkPort saveWalkPort;
	private final Clock clock;

	@Override
	@Transactional
	public StartWalkResult start(StartWalkCommand command) {
		verifyPetAccessPort.requireAccess(command.memberId(), command.petId());
		// 같은 반려동물의 산책 시작이 겹쳐도 진행 중인 산책이 둘이 되지 않도록 먼저 잠근다
		lockPetWalksPort.lock(command.petId());
		// DB가 마이크로초까지 저장하므로 처음부터 맞춘다. 응답의 시각과 나중에 읽은 시각이 같아진다
		Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
		if (loadWalkPort.existsOpenWalkActiveSince(command.petId(), now.minus(Walk.INTERRUPTED_AFTER))) {
			throw new WalkAlreadyInProgressException();
		}
		Walk walk = Walk.start(command.petId(), command.memberId(), now);
		Long walkId = saveWalkPort.save(walk);
		return new StartWalkResult(walkId, walk.getStartedAt(), walk.status(now).name());
	}

}
