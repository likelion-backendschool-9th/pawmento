package com.pawmento.map.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
		}, categories -> Map.of());

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
		var service = new ListPlacesService((area, categories) -> List.of(), categories -> Map.of());

		assertThatThrownBy(() -> service.list(query())).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> service.list(query("CAFE"))).isInstanceOf(IllegalArgumentException.class);
	}

	private static Place place(long id, PlaceCategory category) {
		return new Place(id, category, "장소" + id, null, null, 37.58, 127.0, false, null, null, null);
	}

	@Test
	@DisplayName("#172-S21 응답에 든 장소의 종류 중 가장 오래된 가져온 시각을 싣는다")
	void carriesTheOldestSyncedAtOfTheCategoriesInTheResponse() {
		Instant hospital = Instant.parse("2026-11-21T13:00:00Z");
		Instant grooming = Instant.parse("2026-11-23T13:00:00Z");
		var service = new ListPlacesService((area, categories) -> List.of(place(1, PlaceCategory.HOSPITAL),
				place(2, PlaceCategory.GROOMING)),
				categories -> Map.of(PlaceCategory.HOSPITAL, hospital, PlaceCategory.GROOMING, grooming));

		assertThat(service.list(query("HOSPITAL", "GROOMING")).dataAsOf()).isEqualTo(hospital);
	}

	@Test
	@DisplayName("#172-S21 응답에 든 종류 중 가져온 기록을 모르는 종류가 있으면 null이다(이전 데이터가 실제보다 새것처럼 보이지 않게)")
	void isNullWhenAnIncludedCategoryHasNoSyncRecord() {
		var seen = new Object() {
			Set<PlaceCategory> relevant;
		};
		// 병원만 가져온 기록이 있고, 응답에는 미용 장소(이전 데이터)도 들어 있다
		var service = new ListPlacesService((area, categories) -> List.of(place(1, PlaceCategory.HOSPITAL),
				place(2, PlaceCategory.GROOMING)), categories -> {
					seen.relevant = categories;
					return Map.of(PlaceCategory.HOSPITAL, Instant.parse("2026-11-24T13:00:00Z"));
				});

		assertThat(service.list(query("HOSPITAL", "GROOMING")).dataAsOf()).isNull();
		assertThat(seen.relevant).containsExactlyInAnyOrder(PlaceCategory.HOSPITAL, PlaceCategory.GROOMING);
	}

	@Test
	@DisplayName("#172-S21 응답에 들지 않은 종류의 기록은 따지지 않고, 장소가 하나도 없으면 요청한 종류를 기준으로 한다")
	void usesTheIncludedCategoriesAndFallsBackToTheRequestedOnes() {
		var seen = new Object() {
			Set<PlaceCategory> relevant;
		};
		Instant hospital = Instant.parse("2026-11-24T13:00:00Z");
		// 요청은 병원·미용이지만 영역 안에는 병원만 있다. 가져온 기록 없는 미용은 이번 응답과 상관없다
		var onlyHospital = new ListPlacesService((area, categories) -> List.of(place(1, PlaceCategory.HOSPITAL)),
				categories -> {
					seen.relevant = categories;
					return Map.of(PlaceCategory.HOSPITAL, hospital);
				});
		assertThat(onlyHospital.list(query("HOSPITAL", "GROOMING")).dataAsOf()).isEqualTo(hospital);
		assertThat(seen.relevant).containsExactly(PlaceCategory.HOSPITAL);

		// 장소가 하나도 없으면 요청한 종류가 기준이다. 기록 없는 종류가 있으면 null, 모두 있으면 가장 오래된 시각이다
		var empty = new ListPlacesService((area, categories) -> List.of(), categories -> Map.of(PlaceCategory.HOSPITAL, hospital));
		assertThat(empty.list(query("HOSPITAL", "GROOMING")).dataAsOf()).isNull();
		assertThat(empty.list(query("HOSPITAL")).dataAsOf()).isEqualTo(hospital);
	}

}
