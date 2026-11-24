package com.pawmento.pet.application.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

final class PetDates {
	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	private PetDates() {
	}

	static LocalDate today(Clock clock) {
		return LocalDate.now(clock.withZone(SEOUL));
	}
}
