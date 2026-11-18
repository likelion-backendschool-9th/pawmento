package com.pawmento.map.application.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pawmento.map.application.exception.WalkNotFoundException;
import com.pawmento.map.application.port.in.EndWalkCommand;
import com.pawmento.map.application.port.in.EndWalkResult;
import com.pawmento.map.application.port.in.EndWalkUseCase;
import com.pawmento.map.application.port.out.LoadWalkPort;
import com.pawmento.map.application.port.out.SaveWalkPort;
import com.pawmento.map.domain.Walk;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
class EndWalkService implements EndWalkUseCase {

	private final LoadWalkPort loadWalkPort;
	private final SaveWalkPort saveWalkPort;
	private final Clock clock;

	@Override
	@Transactional
	public EndWalkResult end(EndWalkCommand command) {
		// 좌표를 쌓는 요청과 겹쳐도 상태가 어긋나지 않도록 행을 잠그고 읽는다
		Walk walk = loadWalkPort.loadByIdForUpdate(command.walkId()).orElseThrow(WalkNotFoundException::new);
		// DB가 마이크로초까지 저장하므로 처음부터 맞춘다. 응답의 시각과 나중에 읽은 시각이 같아진다
		Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
		walk.end(command.memberId(), now);
		saveWalkPort.save(walk);
		return new EndWalkResult(walk.getId(), walk.getEndedAt(), walk.status(now).name());
	}

}
