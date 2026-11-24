package com.pawmento.map.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 시나리오는 docs/map/animal-places.md(#172-S7)에 있다
class Open24hRuleTest {

	@Test
	@DisplayName("#172-S7 이름에 24시가 들어 있으면 NAME(이름 기준 추정)이다")
	void nameWith24Hours() {
		assertThat(Open24hRule.fromName("24시 해든동물병원")).isEqualTo(Open24hBasis.NAME);
		assertThat(Open24hRule.fromName("해든 24시 동물병원")).isEqualTo(Open24hBasis.NAME);
		assertThat(Open24hRule.fromName("해든동물병원24시")).isEqualTo(Open24hBasis.NAME);
	}

	@Test
	@DisplayName("#172-S7 진월24동물병원처럼 24시가 아닌 24는 24시간으로 보지 않는다")
	void twentyFourWithoutSi() {
		assertThat(Open24hRule.fromName("진월24동물병원")).isNull();
		assertThat(Open24hRule.fromName("대학로동물병원")).isNull();
		assertThat(Open24hRule.fromName(null)).isNull();
		assertThat(Open24hRule.fromName("")).isNull();
	}

	@Test
	@DisplayName("#172-S7 운영 시간이 00:00~24:00이면 HOURS(제공된 운영 시간)다")
	void hours() {
		assertThat(Open24hRule.fromHours("00:00", "24:00")).isEqualTo(Open24hBasis.HOURS);
		assertThat(Open24hRule.fromHours("09:00", "18:00")).isNull();
		assertThat(Open24hRule.fromHours("00:00", "23:59")).isNull();
		assertThat(Open24hRule.fromHours(null, "24:00")).isNull();
		assertThat(Open24hRule.fromHours("00:00", null)).isNull();
		assertThat(Open24hRule.fromHours(" 00:00 ", " 24:00 ")).isEqualTo(Open24hBasis.HOURS);
	}

}
