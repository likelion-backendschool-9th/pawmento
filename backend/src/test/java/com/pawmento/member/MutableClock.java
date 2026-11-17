package com.pawmento.member;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

final class MutableClock extends Clock {

	private final ZoneId zone;
	private volatile Instant instant;

	MutableClock(Instant instant, ZoneId zone) {
		this.instant = instant;
		this.zone = zone;
	}

	void setInstant(Instant instant) {
		this.instant = instant;
	}

	@Override
	public ZoneId getZone() {
		return zone;
	}

	@Override
	public Clock withZone(ZoneId zone) {
		return new MutableClock(instant, zone);
	}

	@Override
	public Instant instant() {
		return instant;
	}

}
