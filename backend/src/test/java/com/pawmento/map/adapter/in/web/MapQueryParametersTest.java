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

}
