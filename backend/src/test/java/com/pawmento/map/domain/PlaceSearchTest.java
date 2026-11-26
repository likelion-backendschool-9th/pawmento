package com.pawmento.map.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.pawmento.map.domain.exception.InvalidSearchQueryException;

// 시나리오는 docs/map/place-search.md(#195-S2~S6)에 있다. 검색어 정규화, 순서, 겹침 제거, 잘림 규칙을 확인한다
class PlaceSearchTest {

	private static final Set<PlaceCategory> ALL = EnumSet.allOf(PlaceCategory.class);

	private static final double LAT = 37.5800;

	private static final double LNG = 127.0000;

	// 위도 1도는 약 111,195m다
	private static final double METER = 1 / 111_195.0;

	private static Place place(long id, PlaceCategory category, String name, String address, double latitude,
			double longitude) {
		return new Place(id, category, name, address, null, latitude, longitude, false, null, null, null);
	}

	private static Place hospital(long id, String name, String address) {
		return place(id, PlaceCategory.HOSPITAL, name, address, LAT, LNG);
	}

	private static List<Long> ids(PlaceSearch.Result result) {
		return result.hits().stream().map(hit -> hit.place().id()).toList();
	}

	@Test
	@DisplayName("#195-S2 검색어는 띄어쓰기·괄호·구두점·대소문자를 무시하고 비교한다")
	void normalizesTheQuery() {
		assertThat(PlaceSearch.normalize("스테이 동물병원")).isEqualTo("스테이동물병원");
		assertThat(PlaceSearch.normalize("스테이(동물병원)")).isEqualTo("스테이동물병원");
		assertThat(PlaceSearch.normalize(" Stay  Clinic ")).isEqualTo("stayclinic");
		assertThat(PlaceSearch.normalize("%%")).isEqualTo("%%");
	}

	@Test
	@DisplayName("#195-S6 정규화한 검색어가 2자 미만이거나 30자를 넘으면 이유와 함께 거절하고, 길이는 정규화한 뒤에 센다")
	void rejectsTooShortOrTooLongQueries() {
		assertThatThrownBy(() -> PlaceSearch.normalize("서")).isInstanceOf(InvalidSearchQueryException.class)
			.hasMessageContaining("2자 이상");
		assertThatThrownBy(() -> PlaceSearch.normalize("   ")).isInstanceOf(InvalidSearchQueryException.class)
			.hasMessageContaining("2자 이상");
		assertThatThrownBy(() -> PlaceSearch.normalize(null)).isInstanceOf(InvalidSearchQueryException.class);
		// 공백·구두점뿐인 검색어는 정규화하면 비어 거절한다
		assertThatThrownBy(() -> PlaceSearch.normalize("_ _")).isInstanceOf(InvalidSearchQueryException.class);
		assertThatThrownBy(() -> PlaceSearch.normalize("가".repeat(31))).isInstanceOf(InvalidSearchQueryException.class)
			.hasMessageContaining("30자 이하");

		assertThat(PlaceSearch.normalize("가".repeat(30))).hasSize(30);
		assertThat(PlaceSearch.normalize("서울")).isEqualTo("서울");
		// 띄어쓰기를 지운 뒤에 길이를 세므로, 글자가 30자면 띄어쓰기가 많아도 받는다
		assertThat(PlaceSearch.normalize("가 ".repeat(30))).hasSize(30);
	}

	@Test
	@DisplayName("#195-S6 널 문자(U+0000) 같은 제어 문자가 든 검색어는 DB를 부르기 전에 쓸 수 없는 글자로 거절하고, 이유에 검색어를 담지 않는다")
	void rejectsControlCharacters() {
		for (String query : new String[] { "ab\u0000cd", "\u0000\u0000", "서울\u0001숲", "서울\u007f숲" }) {
			assertThatThrownBy(() -> PlaceSearch.normalize(query)).as(query.replace('\u0000', '0'))
				.isInstanceOf(InvalidSearchQueryException.class)
				.hasMessageContaining("쓸 수 없는 글자")
				.hasMessageNotContaining("서울")
				.hasMessageNotContaining("ab");
		}
		// 띄어쓰기·탭·줄바꿈은 정규화에서 지워지므로 거절하지 않는다
		assertThat(PlaceSearch.normalize("서울\t숲\n")).isEqualTo("서울숲");
	}

	@Test
	@DisplayName("#195-S3 지도 중심이 없으면 이름이 검색어로 시작하는 곳, 이름에 든 곳, 주소에만 든 곳 순이고 같은 묶음에서는 이름순이며 거리는 null이다")
	void ordersByRelevanceWithoutCenter() {
		List<Place> candidates = List.of(
				hospital(1, "하나 서울숲 동물병원", "서울 어딘가"),
				hospital(2, "서울숲 펫살롱", "서울 성동구 1"),
				hospital(3, "가나다 병원", "서울 성동구 서울숲4길 18"),
				hospital(4, "서울숲 가 동물병원", "서울 성동구 2"));

		PlaceSearch.Result result = PlaceSearch.select(candidates, "서울숲", ALL, Optional.empty(), 20);

		// 시작(이름순: "서울숲 가 동물병원" < "서울숲 펫살롱") → 이름에 있음 → 주소에만 있음
		assertThat(ids(result)).containsExactly(4L, 2L, 1L, 3L);
		assertThat(result.hits()).allSatisfy(hit -> assertThat(hit.distanceMeters()).isNull());
		assertThat(result.truncated()).isFalse();
	}

	@Test
	@DisplayName("#195-S3 지도 중심을 받으면 가까운 순이고 거리는 정수 미터로 반올림해 담는다")
	void ordersByDistanceWithCenter() {
		List<Place> candidates = List.of(
				place(1, PlaceCategory.HOSPITAL, "강남 A", "주소", LAT + 300 * METER, LNG),
				place(2, PlaceCategory.HOSPITAL, "강남 B", "주소", LAT + 100 * METER, LNG),
				place(3, PlaceCategory.HOSPITAL, "강남 C", "주소", LAT + 1200 * METER, LNG));

		PlaceSearch.Result result = PlaceSearch.select(candidates, "강남", ALL,
				Optional.of(new PlaceSearch.Center(LAT, LNG)), 20);

		assertThat(ids(result)).containsExactly(2L, 1L, 3L);
		assertThat(result.hits().get(0).distanceMeters()).isBetween(99, 101);
		assertThat(result.hits().get(1).distanceMeters()).isBetween(299, 301);
		assertThat(result.hits().get(2).distanceMeters()).isBetween(1199, 1201);
	}

	@Test
	@DisplayName("#195-S3 같은 거리의 장소는 id 순서라 결과가 늘 같다")
	void sortsEqualDistancesById() {
		List<Place> candidates = List.of(hospital(9, "강남 병원", "주소"), hospital(3, "강남 의원", "주소"));

		PlaceSearch.Result result = PlaceSearch.select(candidates, "강남", ALL,
				Optional.of(new PlaceSearch.Center(LAT, LNG)), 20);

		assertThat(ids(result)).containsExactly(3L, 9L);
		assertThat(result.hits()).allSatisfy(hit -> assertThat(hit.distanceMeters()).isZero());
	}

	@Test
	@DisplayName("#195-S4 병원과 미용을 모두 요청하면 같은 이름의 겹치는 미용을 지우고, 미용만 요청하면 그대로 둔다")
	void removesOverlappingGroomingOnlyWhenBothRequested() {
		Place hospital = place(1, PlaceCategory.HOSPITAL, "북악동물병원", "주소", LAT, LNG);
		Place sameName = place(2, PlaceCategory.GROOMING, "북악 동물병원", "주소", LAT + 20 * METER, LNG);
		Place other = place(3, PlaceCategory.GROOMING, "북악 펫살롱", "주소", LAT + 20 * METER, LNG);
		List<Place> candidates = List.of(hospital, sameName, other);

		PlaceSearch.Result both = PlaceSearch.select(candidates, "북악", EnumSet.of(PlaceCategory.HOSPITAL,
				PlaceCategory.GROOMING), Optional.empty(), 20);
		PlaceSearch.Result groomingOnly = PlaceSearch.select(candidates, "북악", EnumSet.of(PlaceCategory.GROOMING),
				Optional.empty(), 20);

		assertThat(ids(both)).containsExactlyInAnyOrder(1L, 3L);
		assertThat(ids(groomingOnly)).containsExactlyInAnyOrder(2L, 3L);
	}

	@Test
	@DisplayName("#195-S4 요청하지 않은 종류는 결과에 없다")
	void dropsCategoriesThatWereNotRequested() {
		List<Place> candidates = List.of(hospital(1, "서울 병원", "주소"),
				place(2, PlaceCategory.SHELTER, "서울 보호센터", "주소", LAT, LNG));

		PlaceSearch.Result result = PlaceSearch.select(candidates, "서울", EnumSet.of(PlaceCategory.SHELTER),
				Optional.empty(), 20);

		assertThat(ids(result)).containsExactly(2L);
	}

	@Test
	@DisplayName("#195-S5 20곳을 넘으면 앞쪽 20곳과 truncated=true를 주고, 정확히 20곳이면 false다")
	void truncatesAfterTwentyPlaces() {
		List<Place> twentyOne = new ArrayList<>();
		for (long id = 1; id <= 21; id++) {
			twentyOne.add(hospital(id, "서울 병원 %02d".formatted(id), "주소"));
		}

		PlaceSearch.Result over = PlaceSearch.select(twentyOne, "서울", ALL, Optional.empty(), 20);
		PlaceSearch.Result exact = PlaceSearch.select(twentyOne.subList(0, 20), "서울", ALL, Optional.empty(), 20);

		assertThat(over.hits()).hasSize(20);
		assertThat(over.truncated()).isTrue();
		assertThat(ids(over)).containsExactlyElementsOf(twentyOne.subList(0, 20).stream().map(Place::id).toList());
		assertThat(exact.hits()).hasSize(20);
		assertThat(exact.truncated()).isFalse();
	}

	@Test
	@DisplayName("#195-S5 잘림은 겹침을 지운 뒤의 개수로 판단한다")
	void truncationCountsAfterRemovingOverlaps() {
		List<Place> candidates = new ArrayList<>();
		for (long id = 1; id <= 20; id++) {
			candidates.add(place(id, PlaceCategory.HOSPITAL, "서울 병원 %02d".formatted(id), "주소", LAT, LNG));
		}
		// 21번째 후보는 첫 병원과 같은 이름의 겹치는 미용이라 지워진다
		candidates.add(place(21, PlaceCategory.GROOMING, "서울병원01", "주소", LAT + 10 * METER, LNG));

		PlaceSearch.Result result = PlaceSearch.select(candidates, "서울", EnumSet.of(PlaceCategory.HOSPITAL,
				PlaceCategory.GROOMING), Optional.empty(), 20);

		assertThat(result.hits()).hasSize(20);
		assertThat(result.truncated()).isFalse();
	}

}
