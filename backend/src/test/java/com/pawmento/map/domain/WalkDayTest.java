package com.pawmento.map.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WalkDayTest {

	@Test
	@DisplayName("#73-S7 하루는 Asia/Seoul 0시부터 다음 날 0시 전까지다")
	void dayIsSeoulMidnightToMidnight() {
		WalkDay day = new WalkDay(LocalDate.of(2026, 11, 17));

		// 서울 11월 17일 0시는 UTC 11월 16일 15시다
		assertThat(day.startInclusive()).isEqualTo(Instant.parse("2026-11-16T15:00:00Z"));
		assertThat(day.endExclusive()).isEqualTo(Instant.parse("2026-11-17T15:00:00Z"));
	}

	@Test
	@DisplayName("#73-S7 연속한 두 날은 빈틈과 겹침 없이 이어진다. 윤일과 해가 바뀌는 날도 같다")
	void consecutiveDaysAreContiguous() {
		for (LocalDate date : new LocalDate[] { LocalDate.of(2028, 2, 28), LocalDate.of(2028, 2, 29),
				LocalDate.of(2026, 12, 31) }) {
			WalkDay day = new WalkDay(date);
			WalkDay next = new WalkDay(date.plusDays(1));

			assertThat(day.endExclusive()).isEqualTo(next.startInclusive());
			assertThat(day.startInclusive()).isBefore(day.endExclusive());
		}
		assertThat(new WalkDay(LocalDate.of(2028, 2, 29)).endExclusive())
			.isEqualTo(Instant.parse("2028-02-29T15:00:00Z"));
	}

	@Test
	@DisplayName("#73-S7 날짜가 없으면 프로그래밍 오류로 거절한다")
	void dateIsRequired() {
		assertThatThrownBy(() -> new WalkDay(null)).isInstanceOf(IllegalArgumentException.class);
	}

}
