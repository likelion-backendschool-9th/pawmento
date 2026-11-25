package com.pawmento.map.adapter.out.publicdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

// 시나리오는 docs/map/animal-places.md(#172-S5, S20)에 있다. 가져오기 설정이 잘못되면 호출이 어긋난 채 실패하지 않고 앱이 시작할 때 거절하는지 확인한다
class PublicDataPropertiesTest {

	private static PublicDataProperties valid(int pageSize, int maxAttempts, Duration connect, Duration read, Duration backoff,
			Duration maxBackoff, Duration interval) {
		return new PublicDataProperties("key", "https://apis.data.go.kr", pageSize, connect, read, maxAttempts, backoff, maxBackoff,
				interval);
	}

	private static PublicDataProperties bind(Map<String, String> values) {
		return new Binder(new MapConfigurationPropertySource(values)).bind("pawmento.map.public-data", PublicDataProperties.class)
			.get();
	}

	@Test
	@DisplayName("#172-S5 설정을 하나도 주지 않으면 기본값이 모두 올바르다")
	void defaultsAreValid() {
		PublicDataProperties defaults = bind(Map.of("pawmento.map.public-data.service-key", "k"));

		assertThat(defaults.pageSize()).isEqualTo(100);
		assertThat(defaults.maxAttempts()).isEqualTo(8);
		assertThat(defaults.readTimeout()).isEqualTo(Duration.ofSeconds(20));
		assertThat(defaults.hasServiceKey()).isTrue();
	}

	@Test
	@DisplayName("#172-S20 pageSize는 1~100만 받는다(0이면 0으로 나누기, 100을 넘으면 페이지 수가 어긋나 매번 totalCount 불일치가 된다)")
	void pageSizeMustBeBetweenOneAndOneHundred() {
		for (int bad : new int[] { 0, -1, 101, 1000 }) {
			assertThatThrownBy(() -> valid(bad, 8, Duration.ofSeconds(5), Duration.ofSeconds(20), Duration.ofSeconds(1),
					Duration.ofSeconds(30), Duration.ofMillis(500)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("page-size");
		}
		assertThat(valid(1, 8, Duration.ofSeconds(5), Duration.ofSeconds(20), Duration.ofSeconds(1), Duration.ofSeconds(30),
				Duration.ZERO).pageSize()).isEqualTo(1);
		assertThat(valid(100, 1, Duration.ofSeconds(5), Duration.ofSeconds(20), Duration.ofSeconds(1), Duration.ofSeconds(30),
				Duration.ZERO).maxAttempts()).isEqualTo(1);
	}

	@Test
	@DisplayName("#172-S5 maxAttempts는 1 이상이고, 연결·읽기·쉬는 시간은 0보다 크며, 최대 쉬는 시간은 시작값 이상이고, 페이지 사이 간격은 0 이상이다")
	void rejectsOtherInvalidValues() {
		var seconds = Duration.ofSeconds(1);
		assertThatThrownBy(() -> valid(100, 0, seconds, seconds, seconds, seconds, seconds)).hasMessageContaining("max-attempts");
		assertThatThrownBy(() -> valid(100, 8, Duration.ZERO, seconds, seconds, seconds, seconds)).hasMessageContaining("connect-timeout");
		assertThatThrownBy(() -> valid(100, 8, seconds, Duration.ofSeconds(-1), seconds, seconds, seconds))
			.hasMessageContaining("read-timeout");
		assertThatThrownBy(() -> valid(100, 8, seconds, seconds, Duration.ZERO, seconds, seconds)).hasMessageContaining("backoff");
		assertThatThrownBy(() -> valid(100, 8, seconds, seconds, Duration.ofSeconds(10), Duration.ofSeconds(5), seconds))
			.hasMessageContaining("max-backoff");
		assertThatThrownBy(() -> valid(100, 8, seconds, seconds, seconds, seconds, Duration.ofMillis(-1)))
			.hasMessageContaining("page-interval");
	}

	@Test
	@DisplayName("#172-S5 환경 설정(application.yml·환경 변수)에서 잘못된 값을 주면 바인딩이 실패해 앱이 시작되지 않는다")
	void invalidConfigurationFailsToBind() {
		assertThatThrownBy(() -> bind(Map.of("pawmento.map.public-data.page-size", "0"))).isInstanceOf(BindException.class);
		assertThatThrownBy(() -> bind(Map.of("pawmento.map.public-data.max-attempts", "0"))).isInstanceOf(BindException.class);
		assertThatThrownBy(() -> bind(Map.of("pawmento.map.public-data.read-timeout", "0s"))).isInstanceOf(BindException.class);
	}

}
