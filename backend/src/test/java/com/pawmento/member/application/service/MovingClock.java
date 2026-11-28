package com.pawmento.member.application.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

// 테스트 중간에 시간을 앞으로 보낼 수 있는 시계. 메일 발송·저장 대기 같은 "걸린 시간"을 흉내 낸다
final class MovingClock extends Clock {

	private Instant instant;

	MovingClock(Instant instant) {
		this.instant = instant;
	}

	void advance(Duration duration) {
		instant = instant.plus(duration);
	}

	@Override
	public ZoneId getZone() {
		return ZoneOffset.UTC;
	}

	@Override
	public Clock withZone(ZoneId zone) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Instant instant() {
		return instant;
	}

}
