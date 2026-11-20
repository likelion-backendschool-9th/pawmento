package com.pawmento.map.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.pawmento.diary.application.port.out.DiaryMapSnapshot;
import com.pawmento.map.application.port.in.DiaryLocationSnapshot;
import com.pawmento.map.domain.DiaryLocationState;
import com.pawmento.map.domain.LocationSource;

class DiaryLocationPortValuesTest {
	@Test
	@DisplayName("#44-S6 지도·입력 계약·diary 위치 출처 enum 이름은 모두 같아야 한다")
	void sourceEnumsStayInSync() {
		var names = Arrays.stream(LocationSource.values()).map(Enum::name).toList();
		assertThat(Arrays.stream(DiaryLocationSnapshot.Source.values()).map(Enum::name).toList())
			.containsExactlyInAnyOrderElementsOf(names);
		assertThat(Arrays.stream(DiaryMapSnapshot.Source.values()).map(Enum::name).toList())
			.containsExactlyInAnyOrderElementsOf(names);
	}

	@Test
	@DisplayName("#44-S6 모든 지도 출처와 null 관측값은 입력 포트 경계를 왕복해도 유지된다")
	void sourceRoundTrip() {
		for (var source : LocationSource.values()) {
			var state = new DiaryLocationState(1L, 2L, 3L, 4L, 37, 127, "공원", source, null, 5);
			assertThat(DiaryLocationPortValues.state(DiaryLocationPortValues.snapshot(state))).isEqualTo(state);
		}
		var unknown = new DiaryLocationState(1L, 2L, 3L, 4L, 37, 127, null, null, null, 5);
		assertThat(DiaryLocationPortValues.state(DiaryLocationPortValues.snapshot(unknown))).isEqualTo(unknown);
	}
}
