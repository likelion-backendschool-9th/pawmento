package com.pawmento.map.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WalkPointTest {

	private static final Instant AT = Instant.parse("2026-11-17T01:00:00Z");

	@Test
	@DisplayName("#73-S4 산책, 좌표, 기록 시각으로 좌표를 만든다")
	void of() {
		WalkPoint point = WalkPoint.of(5L, 37.4979, 127.0276, AT);

		assertThat(point.getWalkId()).isEqualTo(5L);
		assertThat(point.getLatitude()).isEqualTo(37.4979);
		assertThat(point.getLongitude()).isEqualTo(127.0276);
		assertThat(point.getRecordedAt()).isEqualTo(AT);
	}

	@Test
	@DisplayName("#73-S5 위도·경도가 범위를 벗어나거나 숫자가 아니면 만들 수 없고, 경계값은 허용한다")
	void coordinateRange() {
		assertThatThrownBy(() -> WalkPoint.of(5L, 91.0, 127.0, AT)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> WalkPoint.of(5L, -90.1, 127.0, AT)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> WalkPoint.of(5L, 37.0, 180.1, AT)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> WalkPoint.of(5L, Double.NaN, 127.0, AT)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> WalkPoint.of(5L, 37.0, Double.POSITIVE_INFINITY, AT))
			.isInstanceOf(IllegalArgumentException.class);
		WalkPoint.of(5L, 90.0, 180.0, AT);
		WalkPoint.of(5L, -90.0, -180.0, AT);
	}

	@Test
	@DisplayName("#73-S4 산책 id나 기록 시각이 없으면 만들 수 없다")
	void requiresWalkAndTime() {
		assertThatThrownBy(() -> WalkPoint.of(null, 37.0, 127.0, AT)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> WalkPoint.of(5L, 37.0, 127.0, null)).isInstanceOf(IllegalArgumentException.class);
	}

}
