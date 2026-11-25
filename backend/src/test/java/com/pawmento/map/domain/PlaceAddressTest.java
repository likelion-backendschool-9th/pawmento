package com.pawmento.map.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 시나리오는 docs/map/place-address.md(#239-S1, S2)에 있다. 가려진 주소 판정과 건물 이름 붙이기 규칙을 확인한다
class PlaceAddressTest {

	private static final String MASKED = "서울특별시 성동구 서울숲*길 **-**, ***동 *층 ***호 (성수동*가, 갤러리아 포레)";

	private static final String ROAD = "서울특별시 성동구 서울숲2길 32-14";

	@Test
	@DisplayName("#239-S2 주소의 어느 위치든 별표가 있으면 가려진 주소다")
	void maskedWhenAnyAsteriskExists() {
		assertThat(PlaceAddress.isMasked(MASKED)).isTrue();
		assertThat(PlaceAddress.isMasked("서울 성동구 진흥로 ***")).isTrue();
		assertThat(PlaceAddress.isMasked("*")).isTrue();
	}

	@Test
	@DisplayName("#239-S2 별표가 없거나 주소가 비어 있으면 가려진 주소가 아니다")
	void notMaskedWithoutAsterisk() {
		assertThat(PlaceAddress.isMasked("서울 종로구 대학로 1")).isFalse();
		assertThat(PlaceAddress.isMasked("")).isFalse();
		assertThat(PlaceAddress.isMasked(null)).isFalse();
	}

	@Test
	@DisplayName("#239-S1 원본 괄호 안에서 별표가 없는 건물 이름만 도로명 주소 뒤에 붙인다")
	void appendsBuildingNameWithoutAsterisk() {
		assertThat(PlaceAddress.withBuildingName(ROAD, MASKED)).isEqualTo(ROAD + " (갤러리아 포레)");
	}

	@Test
	@DisplayName("#239-S1 괄호 안의 말이 모두 별표를 가지면 도로명 주소만 돌려준다")
	void noBuildingNameWhenEveryPartIsMasked() {
		assertThat(PlaceAddress.withBuildingName(ROAD, "서울 성동구 서울숲*길 ** (성수동*가, ***빌딩)")).isEqualTo(ROAD);
	}

	@Test
	@DisplayName("#239-S1 괄호가 없거나 닫히지 않았거나 원본이 비어 있어도 도로명 주소만 돌려준다")
	void noBuildingNameWithoutProperParentheses() {
		assertThat(PlaceAddress.withBuildingName(ROAD, "서울 성동구 진흥로 ***")).isEqualTo(ROAD);
		assertThat(PlaceAddress.withBuildingName(ROAD, "서울 성동구 진흥로 *** (갤러리아 포레")).isEqualTo(ROAD);
		assertThat(PlaceAddress.withBuildingName(ROAD, null)).isEqualTo(ROAD);
	}

	@Test
	@DisplayName("#239-S1 마지막 괄호에 층·호만 있으면 건물 이름을 붙이지 않는다")
	void doesNotAppendFloorOrUnit() {
		assertThat(PlaceAddress.withBuildingName(ROAD, "서울 성동구 서울숲*길 **-** (1층)")).isEqualTo(ROAD);
		assertThat(PlaceAddress.withBuildingName(ROAD, "서울 성동구 서울숲*길 **-** (지하1층)")).isEqualTo(ROAD);
		assertThat(PlaceAddress.withBuildingName(ROAD, "서울 성동구 서울숲*길 **-** (성수동*가, 301호)")).isEqualTo(ROAD);
		assertThat(PlaceAddress.withBuildingName(ROAD, "서울 성동구 서울숲*길 **-** (2층 201호)")).isEqualTo(ROAD);
	}

	@Test
	@DisplayName("#239-S1 층·호와 건물 이름이 함께 있으면 건물 이름만 붙인다")
	void appendsOnlyBuildingNameBesideFloor() {
		assertThat(PlaceAddress.withBuildingName(ROAD, "서울 성동구 서울숲*길 **-** (갤러리아 포레, 1층)"))
			.isEqualTo(ROAD + " (갤러리아 포레)");
		assertThat(PlaceAddress.withBuildingName(ROAD, "서울 성동구 서울숲*길 **-** (서울숲 2호점)"))
			.isEqualTo(ROAD + " (서울숲 2호점)");
	}

	@Test
	@DisplayName("#239-S1 괄호가 여러 개이면 마지막 괄호의 건물 이름을 쓰고 공백은 다듬는다")
	void usesLastParenthesesAndStripsSpaces() {
		assertThat(PlaceAddress.withBuildingName(ROAD, "서울 성동구 서울숲*길 **-** (1층) (성수동*가 ,  갤러리아 포레 )"))
			.isEqualTo(ROAD + " (갤러리아 포레)");
	}

}
