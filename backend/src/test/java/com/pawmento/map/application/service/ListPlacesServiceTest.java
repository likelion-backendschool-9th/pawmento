package com.pawmento.map.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.pawmento.map.application.port.in.ListPlacesQuery;
import com.pawmento.map.application.port.in.ListPlacesResult;
import com.pawmento.map.domain.Open24hBasis;
import com.pawmento.map.domain.Place;
import com.pawmento.map.domain.PlaceCategory;

// 입력 포트는 도메인 타입을 노출하지 않는다(문자열 계약). 서비스가 도메인으로 바꾸고 결과를 다시 문자열로 돌려주는 부분을 확인한다
class ListPlacesServiceTest {

	private static ListPlacesQuery query(String... categories) {
		return new ListPlacesQuery(new BigDecimal("37.57"), new BigDecimal("126.99"), new BigDecimal("37.59"),
				new BigDecimal("127.01"), Set.of(categories));
	}

	@Test
	@DisplayName("#172-S10 요청한 종류 이름을 도메인 종류로 바꿔 조회하고, 결과는 문자열 계약으로 돌려준다")
	void convertsBetweenContractAndDomain() {
		var seen = new Object() {
			Set<PlaceCategory> categories;
		};
		var service = new ListPlacesService((area, categories) -> {
			seen.categories = categories;
			return List.of(
					new Place(1L, PlaceCategory.SHELTER, "센터", "주소", "051-111-2222", 37.58, 127.0, true,
							Open24hBasis.HOURS, "00:00~24:00", null),
					new Place(2L, PlaceCategory.HOSPITAL, "병원", null, null, 37.58, 127.0, false, null, null, null));
		}, categories -> Optional.empty());

		ListPlacesResult result = service.list(query("HOSPITAL", "SHELTER"));

		assertThat(seen.categories).containsExactlyInAnyOrder(PlaceCategory.HOSPITAL, PlaceCategory.SHELTER);
		assertThat(result.places()).hasSize(2);
		ListPlacesResult.Item shelter = result.places().get(0);
		assertThat(shelter.category()).isEqualTo("SHELTER");
		assertThat(shelter.open24hBasis()).isEqualTo("HOURS");
		assertThat(shelter.closedDays()).isNull();
		ListPlacesResult.Item hospital = result.places().get(1);
		assertThat(hospital.category()).isEqualTo("HOSPITAL");
		assertThat(hospital.open24hBasis()).isNull();
		assertThat(hospital.address()).isNull();
		assertThat(result.truncated()).isFalse();
	}

	@Test
	@DisplayName("#172-S11 종류가 비어 있거나 모르는 이름이면 호출한 쪽의 버그로 거절한다")
	void rejectsEmptyOrUnknownCategories() {
		var service = new ListPlacesService((area, categories) -> List.of(), categories -> Optional.empty());

		assertThatThrownBy(() -> service.list(query())).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> service.list(query("CAFE"))).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("#172-S21 요청한 종류의 가져온 시각을 응답에 싣고, 가져온 적이 없으면 null이다")
	void carriesTheDataAsOfTime() {
		var seen = new Object() {
			Set<PlaceCategory> categories;
		};
		Instant oldest = Instant.parse("2026-11-21T13:00:00Z");
		var withRun = new ListPlacesService((area, categories) -> List.of(), categories -> {
			seen.categories = categories;
			return Optional.of(oldest);
		});
		var withoutRun = new ListPlacesService((area, categories) -> List.of(), categories -> Optional.empty());

		assertThat(withRun.list(query("HOSPITAL", "SHELTER")).dataAsOf()).isEqualTo(oldest);
		assertThat(seen.categories).containsExactlyInAnyOrder(PlaceCategory.HOSPITAL, PlaceCategory.SHELTER);
		assertThat(withoutRun.list(query("HOSPITAL")).dataAsOf()).isNull();
	}

}
