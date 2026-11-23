package com.pawmento.map.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.pawmento.map.adapter.in.web.exception.InvalidMapRequestException;

class MapQueryParametersTest {

	private static MapQueryParameters parse(String query) {
		return MapQueryParameters.parse(query, "petId", "date");
	}

	@Test
	@DisplayName("#73-S7 petId와 date를 쿼리 문자열에서 읽고, 인코딩한 값과 인코딩한 키도 읽는다")
	void readsValues() {
		MapQueryParameters query = parse("petId=10&date=2026-11-17");
		assertThat(query.positiveLong("petId")).isEqualTo(10L);
		assertThat(query.date("date")).isEqualTo(LocalDate.of(2026, 11, 17));

		MapQueryParameters encoded = parse("%70etId=%31%30&date=2026-11-17");
		assertThat(encoded.positiveLong("petId")).isEqualTo(10L);
		assertThat(parse("petId=9223372036854775807&date=2028-02-29").positiveLong("petId"))
			.isEqualTo(Long.MAX_VALUE);
		assertThat(parse("petId=1&date=2028-02-29").date("date")).isEqualTo(LocalDate.of(2028, 2, 29));
	}

	@Test
	@DisplayName("#73-S7 키가 없거나 값이 비어 있으면 거절하고, 쿼리 문자열이 없어도 같다")
	void missingValues() {
		assertThatThrownBy(() -> parse(null).positiveLong("petId")).isInstanceOf(InvalidMapRequestException.class)
			.hasMessage("petId를 입력해 주세요.");
		assertThatThrownBy(() -> parse("").date("date")).isInstanceOf(InvalidMapRequestException.class)
			.hasMessage("date를 입력해 주세요.");
		assertThatThrownBy(() -> parse("petId=&date=2026-11-17")).hasMessage("petId를 입력해 주세요.");
		assertThatThrownBy(() -> parse("petId&date=2026-11-17")).hasMessage("petId를 입력해 주세요.");
		assertThatThrownBy(() -> parse("date=2026-11-17").positiveLong("petId")).hasMessage("petId를 입력해 주세요.");
	}

	@Test
	@DisplayName("#73-S7 같은 키가 둘 이상이면 거절한다. 인코딩한 같은 키도 중복이다")
	void duplicates() {
		assertThatThrownBy(() -> parse("petId=1&petId=2")).isInstanceOf(InvalidMapRequestException.class)
			.hasMessage("petId는 한 번만 보내 주세요.");
		assertThatThrownBy(() -> parse("petId=1&%70etId=2")).hasMessage("petId는 한 번만 보내 주세요.");
		assertThatThrownBy(() -> parse("date=2026-11-17&date=2026-11-18"))
			.hasMessage("date는 한 번만 보내 주세요.");
	}

	@Test
	@DisplayName("#73-S7 petId는 1 이상의 Long 정수만 받는다")
	void invalidPetId() {
		for (String value : new String[] { "0", "-1", "+1", "1.5", "1e3", "abc", "%201", "1%20", "9223372036854775808",
				"99999999999999999999", "%D9%A1" }) {
			assertThatThrownBy(() -> parse("petId=" + value + "&date=2026-11-17").positiveLong("petId"))
				.as("petId=%s", value)
				.isInstanceOf(InvalidMapRequestException.class)
				.hasMessage("petId는 1 이상의 정수로 입력해 주세요.");
		}
	}

	@Test
	@DisplayName("#73-S7 date는 정확히 YYYY-MM-DD만 받는다. offset 접미사, 시각, 달력에 없는 날, 다른 형식은 거절한다")
	void invalidDate() {
		for (String value : new String[] { "2026-11-17Z", "2026-11-17%2B09:00", "2026-11-17T10:00",
				"2026-11-17T10:00:00Z", "2026-02-30", "2026-13-01", "2026-00-10", "2026-11-00", "20261117", "2026-1-7",
				"26-11-17", "abc", "2026-11-17%20", "%EF%BC%92026-11-17", "2026-11-17-09:00", "%2B10000-01-01",
				"+10000-01-01", "-0001-01-01", "2026-11-17%2B09:00[Asia/Seoul]" }) {
			assertThatThrownBy(() -> parse("petId=1&date=" + value).date("date")).as("date=%s", value)
				.isInstanceOf(InvalidMapRequestException.class)
				.hasMessage("date는 YYYY-MM-DD 형식으로 입력해 주세요.");
		}
	}

	@Test
	@DisplayName("#19-S5 위도·경도는 부호 있는 십진수로 읽고, 경계값과 지수 표기(1e-7)도 읽는다")
	void readsCoordinates() {
		MapQueryParameters query = MapQueryParameters.parse(
				"minLatitude=-90&maxLatitude=90.0&minLongitude=-180&maxLongitude=180&a=1e-7&b=0&c=-0.5&d=37.123456789",
				"minLatitude", "maxLatitude", "minLongitude", "maxLongitude", "a", "b", "c", "d");

		assertThat(query.latitude("minLatitude")).isEqualByComparingTo("-90");
		assertThat(query.latitude("maxLatitude")).isEqualByComparingTo("90.0");
		assertThat(query.longitude("minLongitude")).isEqualByComparingTo("-180");
		assertThat(query.longitude("maxLongitude")).isEqualByComparingTo("180");
		assertThat(query.latitude("a")).isEqualByComparingTo("1e-7");
		assertThat(query.latitude("b")).isZero();
		assertThat(query.latitude("c")).isEqualByComparingTo("-0.5");
		assertThat(query.longitude("d")).isEqualByComparingTo("37.123456789");
	}

	@Test
	@DisplayName("#19-S5 위도·경도가 숫자가 아니면 거절한다. NaN, Infinity, 16진수, 접미사, 공백, 소수점만 있는 값, 범위를 넘는 지수도 같다")
	void invalidCoordinateFormats() {
		for (String value : new String[] { "abc", "NaN", "Infinity", "-Infinity", "0x1p3", "1d", "1f", "1L", "%201",
				"1%20", ".5", "5.", "+5", "--5", "1e", "1e999", "-1e999", "1,5", "%EF%BC%95" }) {
			assertThatThrownBy(() -> MapQueryParameters.parse("lat=" + value, "lat").latitude("lat"))
				.as("lat=%s", value)
				.isInstanceOf(InvalidMapRequestException.class)
				.hasMessage("lat는 숫자로 입력해 주세요.");
		}
	}

	@Test
	@DisplayName("#19-S5 위도는 -90~90, 경도는 -180~180을 벗어나면 거절하고 문구는 기존과 같다")
	void coordinateRanges() {
		for (String value : new String[] { "90.0000001", "-90.0000001", "91", "-91", "1e2" }) {
			assertThatThrownBy(() -> MapQueryParameters.parse("lat=" + value, "lat").latitude("lat")).as("lat=%s", value)
				.hasMessage("위도는 -90 이상 90 이하로 입력해 주세요.");
		}
		for (String value : new String[] { "180.0000001", "-180.0000001", "181", "-181", "1e3" }) {
			assertThatThrownBy(() -> MapQueryParameters.parse("lng=" + value, "lng").longitude("lng")).as("lng=%s", value)
				.hasMessage("경도는 -180 이상 180 이하로 입력해 주세요.");
		}
		assertThat(MapQueryParameters.parse("lat=100", "lat").longitude("lat")).isEqualByComparingTo("100");
	}

	@Test
	@DisplayName("#19-S5 double로 반올림되기 전의 십진수로 범위를 검사한다. 90.000000000000001은 위도 범위 밖이다")
	void rangeIsCheckedOnTheDecimalValue() {
		for (String value : new String[] { "90.000000000000001", "-90.000000000000001" }) {
			assertThatThrownBy(() -> MapQueryParameters.parse("lat=" + value, "lat").latitude("lat")).as("lat=%s", value)
				.isInstanceOf(InvalidMapRequestException.class)
				.hasMessage("위도는 -90 이상 90 이하로 입력해 주세요.");
		}
		for (String value : new String[] { "180.000000000000001", "-180.000000000000001" }) {
			assertThatThrownBy(() -> MapQueryParameters.parse("lng=" + value, "lng").longitude("lng")).as("lng=%s", value)
				.hasMessage("경도는 -180 이상 180 이하로 입력해 주세요.");
		}
	}

	@Test
	@DisplayName("#73-S7 잘못된 인코딩은 받는 키에서만 거절하고, 이 API가 받지 않는 키는 값과 인코딩을 보지 않는다")
	void encodingIsCheckedOnlyForAcceptedKeys() {
		assertThatThrownBy(() -> parse("petId=%ZZ")).isInstanceOf(InvalidMapRequestException.class)
			.hasMessage("쿼리 문자열의 인코딩이 올바르지 않습니다.");
		assertThatThrownBy(() -> parse("petId=%")).hasMessage("쿼리 문자열의 인코딩이 올바르지 않습니다.");
		assertThatThrownBy(() -> parse("petId=%FF")).hasMessage("쿼리 문자열의 인코딩이 올바르지 않습니다.");

		// 추적용 파라미터처럼 받지 않는 키는 인코딩이 잘못돼도 요청에 영향이 없다
		MapQueryParameters query = parse("petId=1&date=2026-11-17&utm=%E0&%E0=x&q=%&flag&&");
		assertThat(query.positiveLong("petId")).isEqualTo(1L);
		assertThat(query.date("date")).isEqualTo(LocalDate.of(2026, 11, 17));
	}

	@Test
	@DisplayName("#172-S11 categories는 쉼표로 구분한 이름을 읽고, 보내지 않으면 세 종류 모두다")
	void readsPlaceCategories() {
		assertThat(MapQueryParameters.parse("categories=SHELTER,HOSPITAL", "categories").placeCategories("categories"))
			.containsExactlyInAnyOrder("HOSPITAL", "SHELTER");
		assertThat(MapQueryParameters.parse(null, "categories").placeCategories("categories"))
			.containsExactlyInAnyOrder("HOSPITAL", "GROOMING", "SHELTER");
		assertThat(MapQueryParameters.parse("other=1", "categories").placeCategories("categories")).hasSize(3);
	}

	@Test
	@DisplayName("#172-S13 categories의 없는 종류·소문자·빈 항목·공백·중복은 거절한다")
	void rejectsInvalidPlaceCategories() {
		for (String value : new String[] { "CAFE", "hospital", "HOSPITAL,", ",HOSPITAL", "HOSPITAL,,GROOMING",
				"HOSPITAL%20,GROOMING", "HOSPITAL,HOSPITAL" }) {
			assertThatThrownBy(() -> MapQueryParameters.parse("categories=" + value, "categories")
				.placeCategories("categories")).as(value)
				.isInstanceOf(InvalidMapRequestException.class)
				.hasMessageContaining("categories");
		}
		// 값이 비어 있으면 키가 없는 것과 다르게 거절한다
		assertThatThrownBy(() -> MapQueryParameters.parse("categories=", "categories"))
			.hasMessage("categories를 입력해 주세요.");
	}

}
