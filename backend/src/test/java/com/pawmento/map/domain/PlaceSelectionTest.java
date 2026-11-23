package com.pawmento.map.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 장소를 고르는 규칙(docs/map/animal-places.md #172-S11, S12, S15)을 DB 없이 확인한다
class PlaceSelectionTest {

	private static final MapArea AREA = MapArea.of(37.57, 126.99, 37.59, 127.01);

	private static final Set<PlaceCategory> BOTH = EnumSet.of(PlaceCategory.HOSPITAL, PlaceCategory.GROOMING);

	// 위도 1도는 약 111,195m다
	private static final double LAT_PER_METER = 1 / 111_195.0;

	private static Place place(long id, PlaceCategory category, String name, double latitude, double longitude) {
		return new Place(id, category, name, null, null, latitude, longitude, false, null, null, null);
	}

	@Test
	@DisplayName("#172-S15 병원과 미용을 둘 다 요청하면 같은 이름이 29m 안인 미용만 지워지고, 30m 경계는 지워진다")
	void removesGroomingNearSameNameHospital() {
		Place hospital = place(1, PlaceCategory.HOSPITAL, "북악", 37.58, 127.0);
		Place near = place(2, PlaceCategory.GROOMING, "북악", 37.58 + 29 * LAT_PER_METER, 127.0);
		Place far = place(3, PlaceCategory.GROOMING, "북악", 37.58 + 31 * LAT_PER_METER, 127.0);
		Place otherName = place(4, PlaceCategory.GROOMING, "다른곳", 37.58, 127.0);

		var selected = PlaceSelection.select(List.of(hospital, near, far, otherName), AREA, BOTH, 500);

		assertThat(selected.places()).extracting(Place::id).containsExactlyInAnyOrder(1L, 3L, 4L);
	}

	@Test
	@DisplayName("#172-S15 겹침은 병원 쪽을 남긴다. 병원 둘이 겹쳐도 지우지 않는다")
	void keepsHospitalsEvenWhenTheyOverlap() {
		Place first = place(1, PlaceCategory.HOSPITAL, "북악", 37.58, 127.0);
		Place second = place(2, PlaceCategory.HOSPITAL, "북악", 37.58, 127.0);

		var selected = PlaceSelection.select(List.of(first, second), AREA, BOTH, 500);

		assertThat(selected.places()).hasSize(2);
	}

	@Test
	@DisplayName("#172-S15 미용만 요청하거나 병원이 후보에 없으면 아무것도 지우지 않는다")
	void doesNotRemoveWithoutBothCategories() {
		Place hospital = place(1, PlaceCategory.HOSPITAL, "북악", 37.58, 127.0);
		Place grooming = place(2, PlaceCategory.GROOMING, "북악", 37.58, 127.0);

		var groomingOnly = PlaceSelection.select(List.of(hospital, grooming), AREA,
				EnumSet.of(PlaceCategory.GROOMING), 500);

		assertThat(groomingOnly.places()).extracting(Place::id).containsExactly(2L);
		assertThat(PlaceSelection.select(List.of(grooming), AREA, BOTH, 500).places()).hasSize(1);
	}

	@Test
	@DisplayName("#172-S11 요청하지 않은 종류는 후보에 섞여 있어도 나오지 않는다")
	void dropsUnrequestedCategories() {
		Place hospital = place(1, PlaceCategory.HOSPITAL, "병원", 37.58, 127.0);
		Place shelter = place(2, PlaceCategory.SHELTER, "센터", 37.58, 127.0);

		var selected = PlaceSelection.select(List.of(hospital, shelter), AREA, EnumSet.of(PlaceCategory.SHELTER), 500);

		assertThat(selected.places()).extracting(Place::id).containsExactly(2L);
	}

	@Test
	@DisplayName("#172-S12 영역 가운데에 가까운 순서이고, 거리가 같으면 id가 작은 것이 먼저다")
	void sortsByDistanceThenId() {
		// 중심은 (37.58, 127.0)이다. 3번과 5번은 중심에서 같은 거리(위도로 100m)다
		Place center = place(9, PlaceCategory.HOSPITAL, "중심", 37.58, 127.0);
		Place north = place(5, PlaceCategory.HOSPITAL, "북쪽", 37.58 + 100 * LAT_PER_METER, 127.0);
		Place south = place(3, PlaceCategory.HOSPITAL, "남쪽", 37.58 - 100 * LAT_PER_METER, 127.0);
		Place far = place(1, PlaceCategory.HOSPITAL, "먼곳", 37.585, 127.0);

		var selected = PlaceSelection.select(List.of(far, north, center, south), AREA, BOTH, 500);

		assertThat(selected.places()).extracting(Place::id).containsExactly(9L, 3L, 5L, 1L);
	}

	@Test
	@DisplayName("#172-S12 500곳이면 truncated=false, 501곳이면 가장 먼 한 곳이 빠지고 true다")
	void truncatesAfterLimit() {
		List<Place> places = new ArrayList<>();
		for (int i = 0; i <= 500; i++) {
			places.add(place(i + 1, PlaceCategory.HOSPITAL, "병원" + i, 37.58 + i * 0.00001, 127.0));
		}

		var over = PlaceSelection.select(places, AREA, BOTH, 500);
		var exact = PlaceSelection.select(places.subList(0, 500), AREA, BOTH, 500);

		assertThat(over.places()).hasSize(500);
		assertThat(over.truncated()).isTrue();
		assertThat(over.places().get(499).name()).isEqualTo("병원499");
		assertThat(exact.places()).hasSize(500);
		assertThat(exact.truncated()).isFalse();
	}

	@Test
	@DisplayName("#172-S15 겹친 미용을 지운 뒤의 개수로 잘렸는지 판단한다")
	void truncationCountsAfterRemovingOverlaps() {
		List<Place> places = new ArrayList<>();
		for (int i = 0; i < 300; i++) {
			places.add(place(i + 1, PlaceCategory.HOSPITAL, "병원" + i, 37.58 + i * 0.00001, 127.0));
			// 100곳은 같은 이름·같은 자리라 지워진다
			places.add(place(1000 + i, PlaceCategory.GROOMING, i < 100 ? "병원" + i : "미용" + i,
					37.58 + i * 0.00001, 127.0));
		}

		var selected = PlaceSelection.select(places, AREA, BOTH, 500);

		assertThat(selected.places()).hasSize(500);
		assertThat(selected.truncated()).isFalse();
	}

	@Test
	@DisplayName("하버사인 거리는 위도 0.001도에 약 111m이고, 같은 점은 0이다")
	void distance() {
		assertThat(PlaceSelection.meters(37.58, 127.0, 37.58, 127.0)).isZero();
		assertThat(PlaceSelection.meters(37.58, 127.0, 37.581, 127.0)).isBetween(110.5, 111.5);
	}

}
