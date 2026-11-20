package com.pawmento.map.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.pawmento.map.domain.exception.InvalidCoordinatesException;
import com.pawmento.map.domain.exception.InvalidMapAreaException;

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
		assertThatThrownBy(() -> MapArea.of(37.6, 126.9, 37.4, 127.1)).isInstanceOf(InvalidMapAreaException.class);
		assertThatThrownBy(() -> MapArea.of(37.4, 127.1, 37.6, 126.9)).isInstanceOf(InvalidMapAreaException.class);
	}

	@Test
	@DisplayName("#19-S5 위도·경도 범위를 벗어난 영역은 만들 수 없다")
	void rejectsOutOfRange() {
		assertThatThrownBy(() -> MapArea.of(-91.0, 0.0, 0.0, 0.0)).isInstanceOf(InvalidCoordinatesException.class);
		assertThatThrownBy(() -> MapArea.of(0.0, 0.0, 91.0, 0.0)).isInstanceOf(InvalidCoordinatesException.class);
		assertThatThrownBy(() -> MapArea.of(0.0, -181.0, 0.0, 0.0)).isInstanceOf(InvalidCoordinatesException.class);
		assertThatThrownBy(() -> MapArea.of(0.0, 0.0, 0.0, 181.0)).isInstanceOf(InvalidCoordinatesException.class);
		assertThatThrownBy(() -> MapArea.of(Double.NaN, 0.0, 0.0, 0.0)).isInstanceOf(InvalidCoordinatesException.class);
	}

	@Test
	@DisplayName("#19-S5 십진수로 받은 영역은 반올림 전의 값으로 순서를 검사한다. double로는 같아지는 1e-400과 0도 역전이다")
	void comparesOrderOnTheDecimalValue() {
		assertThatThrownBy(() -> MapArea.of(new BigDecimal("1e-400"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO))
			.isInstanceOf(InvalidMapAreaException.class);
		assertThatThrownBy(() -> MapArea.of(BigDecimal.ZERO, new BigDecimal("37.0000000000000001"), BigDecimal.ZERO,
				new BigDecimal("37"))).isInstanceOf(InvalidMapAreaException.class);

		MapArea area = MapArea.of(new BigDecimal("-1e-400"), new BigDecimal("126.5"), BigDecimal.ZERO,
				new BigDecimal("127"));
		assertThat(area.getMinLatitude()).isZero();
		assertThat(area.getMinLongitude()).isEqualTo(126.5);
		assertThat(MapArea.of(BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ONE, BigDecimal.TEN).getMaxLongitude())
			.isEqualTo(10.0);
	}

}
