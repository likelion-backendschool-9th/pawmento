package com.pawmento.map.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 시나리오는 docs/map/animal-places.md(#172-S6)에 있다
class PlacePhoneTest {

	@Test
	@DisplayName("#172-S6 하이픈 없는 10자리·9자리 서울 번호에 하이픈을 붙인다")
	void formatsSeoulNumbers() {
		assertThat(PlacePhone.normalize("0222733233", null)).isEqualTo("02-2273-3233");
		assertThat(PlacePhone.normalize("027339006", null)).isEqualTo("02-733-9006");
	}

	@Test
	@DisplayName("#172-S6 8자리는 서울 주소일 때만 앞에 02를 붙이고, 그 밖에는 걸 수 있는 번호로 저장하지 않는다")
	void eightDigitsNeedSeoulAddress() {
		assertThat(PlacePhone.normalize("36728441", "서울특별시 종로구 대학로 1")).isEqualTo("02-3672-8441");
		assertThat(PlacePhone.normalize("36728441", "경기도 성남시 분당구 1")).isNull();
		assertThat(PlacePhone.normalize("36728441", null)).isNull();
		assertThat(PlacePhone.normalize("36728441", "")).isNull();
	}

	@Test
	@DisplayName("#172-S6 빈 값과 숫자가 아닌 값은 없음이다")
	void blankIsNull() {
		assertThat(PlacePhone.normalize(null, null)).isNull();
		assertThat(PlacePhone.normalize("", null)).isNull();
		assertThat(PlacePhone.normalize("   ", null)).isNull();
		assertThat(PlacePhone.normalize("없음", null)).isNull();
	}

	@Test
	@DisplayName("#172-S6 이미 하이픈이 있는 번호(API 형식)도 같은 결과로 정리하고, 공백과 괄호는 무시한다")
	void acceptsApiFormat() {
		assertThat(PlacePhone.normalize("02-2273-3233", null)).isEqualTo("02-2273-3233");
		assertThat(PlacePhone.normalize("031-123-4567", null)).isEqualTo("031-123-4567");
		assertThat(PlacePhone.normalize("031-1234-5678", null)).isEqualTo("031-1234-5678");
		assertThat(PlacePhone.normalize(" (02) 2273 3233 ", null)).isEqualTo("02-2273-3233");
	}

	@Test
	@DisplayName("#172-S6 0으로 시작하는 10·11자리 지역번호와 휴대전화는 3-3-4, 3-4-4로 붙인다")
	void formatsOtherAreaCodes() {
		assertThat(PlacePhone.normalize("0511234567", null)).isEqualTo("051-123-4567");
		assertThat(PlacePhone.normalize("05112345678", null)).isEqualTo("051-1234-5678");
		assertThat(PlacePhone.normalize("01012345678", null)).isEqualTo("010-1234-5678");
		assertThat(PlacePhone.normalize("07012345678", null)).isEqualTo("070-1234-5678");
	}

	@Test
	@DisplayName("#172-S6 0으로 시작하지 않거나 자릿수가 맞지 않는 번호는 걸 수 있는 번호로 저장하지 않는다")
	void rejectsUndialableNumbers() {
		// 0으로 시작하지 않는다
		assertThat(PlacePhone.normalize("1234567890", null)).isNull();
		// 자릿수가 너무 짧거나 길다
		assertThat(PlacePhone.normalize("02123", null)).isNull();
		assertThat(PlacePhone.normalize("0212345678901", null)).isNull();
		// 02는 9~10자리, 그 밖의 지역번호는 10~11자리다
		assertThat(PlacePhone.normalize("02123456789", null)).isNull();
		assertThat(PlacePhone.normalize("031123456", null)).isNull();
	}

	@Test
	@DisplayName("#172-S6 내선·설명에 붙은 숫자를 본 번호에 합쳐 다른 번호를 만들지 않는다. 확신할 수 없으면 없음이다")
	void doesNotMergeExtensionDigits() {
		// 합치면 0273390061 → 02-7339-0061, 03112345678 → 031-1234-5678처럼 우연히 길이가 맞는 다른 번호가 된다
		assertThat(PlacePhone.normalize("02-733-9006 (내선 1)", null)).isNull();
		assertThat(PlacePhone.normalize("031-123-4567 내선 8", null)).isNull();
		assertThat(PlacePhone.normalize("02-733-9006 (1)", null)).isNull();
		assertThat(PlacePhone.normalize("02-733-9006,1", null)).isNull();
		assertThat(PlacePhone.normalize("02-733-9006 / 1", null)).isNull();
		assertThat(PlacePhone.normalize("02-733-9006~7", null)).isNull();
		// 번호가 둘 붙어 있는 값
		assertThat(PlacePhone.normalize("02-733-9006 031-123-4567", null)).isNull();
		assertThat(PlacePhone.normalize("02-733-9006/031-123-4567", null)).isNull();
		// 번호 구조를 알 수 없는 구분
		assertThat(PlacePhone.normalize("02-73-39-006", null)).isNull();
		assertThat(PlacePhone.normalize("02.733.9006", null)).isNull();
		assertThat(PlacePhone.normalize("tel 02-733-9006", null)).isNull();
	}

	@Test
	@DisplayName("#172-S6 단순한 공백·하이픈·지역번호 괄호는 그대로 정리한다")
	void keepsSimpleFormatting() {
		assertThat(PlacePhone.normalize("02-733-9006", null)).isEqualTo("02-733-9006");
		assertThat(PlacePhone.normalize("02 733 9006", null)).isEqualTo("02-733-9006");
		assertThat(PlacePhone.normalize("(02)733-9006", null)).isEqualTo("02-733-9006");
		assertThat(PlacePhone.normalize("(031) 123-4567", null)).isEqualTo("031-123-4567");
		assertThat(PlacePhone.normalize("3672-8441", "서울특별시 종로구")).isEqualTo("02-3672-8441");
		assertThat(PlacePhone.normalize("3672-8441", "경기도 성남시")).isNull();
	}

}
