package com.pawmento.map.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MapAreaTest {

	@Test
	@DisplayName("#19-S5 영역을 만들면 네 값이 그대로 담긴다")
	void of() {
		MapArea area = MapArea.of(37.4, 126.9, 37.6, 127.1);

		assertThat(area.getMinLatitude()).isEqualTo(37.4);
		assertThat(area.getMinLongitude()).isEqualTo(126.9);
		assertThat(area.getMaxLatitude()).isEqualTo(37.6);
		assertThat(area.getMaxLongitude()).isEqualTo(127.1);
	}

	@Test
	@DisplayName("#19-S5 최솟값과 최댓값이 같은 점 영역은 만들 수 있다")
	void allowsEqualBounds() {
		assertThat(MapArea.of(37.5, 127.0, 37.5, 127.0).getMinLatitude()).isEqualTo(37.5);
	}

	@Test
	@DisplayName("#19-S5 최솟값이 최댓값보다 크면 만들 수 없다")
	void rejectsMinGreaterThanMax() {
		assertThatThrownBy(() -> MapArea.of(37.6, 126.9, 37.4, 127.1)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> MapArea.of(37.4, 127.1, 37.6, 126.9)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("#19-S5 위도·경도 범위를 벗어난 영역은 만들 수 없다")
	void rejectsOutOfRange() {
		assertThatThrownBy(() -> MapArea.of(-91.0, 0.0, 0.0, 0.0)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> MapArea.of(0.0, 0.0, 91.0, 0.0)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> MapArea.of(0.0, -181.0, 0.0, 0.0)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> MapArea.of(0.0, 0.0, 0.0, 181.0)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> MapArea.of(Double.NaN, 0.0, 0.0, 0.0)).isInstanceOf(IllegalArgumentException.class);
	}

}
