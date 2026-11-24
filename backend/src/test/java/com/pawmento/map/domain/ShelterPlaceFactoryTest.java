package com.pawmento.map.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 시나리오는 docs/map/animal-places.md(#172-S7, S8)에 있다. 보호센터 행 하나를 장소로 바꾸는 규칙을 값으로 확인한다
class ShelterPlaceFactoryTest {

	private static ShelterRecord record(String lat, String lng, String open, String close, String closeDay) {
		return new ShelterRecord("327346201500001", " (사)대구수의사회 ", " 대구광역시 북구 호국로 229 (서변동) 6층 ", "053-764-3708",
				lat, lng, open, close, closeDay, "2025-01-03");
	}

	private static PlaceDraft draft(ShelterRecord record) {
		var outcome = ShelterPlaceFactory.create(record);
		assertThat(outcome.excluded()).isFalse();
		return outcome.draft();
	}

	@Test
	@DisplayName("#172-S8 위도·경도가 있는 센터는 등록번호를 키로 SHELTER 장소가 되고, 이름·주소는 앞뒤 공백을 지우며 좌표는 변환하지 않는다")
	void becomesShelterPlace() {
		PlaceDraft draft = draft(record("35.922276", "128.59904", "09:00", "18:00", "토요일+일요일"));

		assertThat(draft.category()).isEqualTo(PlaceCategory.SHELTER);
		assertThat(draft.sourceId()).isEqualTo("327346201500001");
		assertThat(draft.name()).isEqualTo("(사)대구수의사회");
		assertThat(draft.address()).isEqualTo("대구광역시 북구 호국로 229 (서변동) 6층");
		assertThat(draft.phone()).isEqualTo("053-764-3708");
		assertThat(draft.latitude()).isEqualTo(35.922276);
		assertThat(draft.longitude()).isEqualTo(128.59904);
		assertThat(draft.sourceUpdatedAt()).isEqualTo(Instant.parse("2025-01-02T15:00:00Z"));
	}

	@Test
	@DisplayName("#172-S7 운영 시간이 00:00~24:00이면 24시간(HOURS)이고, 그 밖의 시간은 24시간이 아니며 이름 기반(NAME)으로 보지 않는다")
	void open24hFromHours() {
		PlaceDraft always = draft(record("35.9", "128.5", "00:00", "24:00", null));
		PlaceDraft daytime = draft(record("35.9", "128.5", "09:00", "18:00", null));

		assertThat(always.open24h()).isTrue();
		assertThat(always.open24hBasis()).isEqualTo(Open24hBasis.HOURS);
		assertThat(always.openingHours()).isEqualTo("00:00~24:00");
		assertThat(daytime.open24h()).isFalse();
		assertThat(daytime.open24hBasis()).isNull();
		assertThat(daytime.openingHours()).isEqualTo("09:00~18:00");
	}

	@Test
	@DisplayName("#172-S8 운영 시간은 시작과 끝이 모두 있을 때만 만든다. 한쪽이 없거나 비어 있으면 정보 없음(null)이다")
	void openingHoursNeedBothEnds() {
		assertThat(draft(record("35.9", "128.5", "09:00", null, null)).openingHours()).isNull();
		assertThat(draft(record("35.9", "128.5", "", "18:00", null)).openingHours()).isNull();
		assertThat(draft(record("35.9", "128.5", null, null, null)).openingHours()).isNull();
	}

	@Test
	@DisplayName("#172-S8 휴무일은 요일·공휴일 글자(+로 이음)이거나 단독 연중무휴일 때만 원본 그대로 저장하고, 없거나 빈 값, 숫자 값, 약어(월)·설명(격주 토요일)·연중무휴와 다른 값의 조합은 정보 없음(null)이다")
	void closedDaysRule() {
		for (String ok : new String[] { "토요일+일요일", "일요일", "일요일+공휴일", "토요일+일요일+공휴일", "연중무휴", "월요일" }) {
			assertThat(draft(record("35.9", "128.5", null, null, ok)).closedDays()).as(ok).isEqualTo(ok);
		}
		for (String unknown : new String[] { null, "", "  ", "0", "1", "2", "1+15+10", "토요일+0", "휴무일 없음", "토요일+", "월",
				"격주 토요일", "명절", "연중무휴+일요일", "일요일+연중무휴", "연중무휴+연중무휴" }) {
			assertThat(draft(record("35.9", "128.5", null, null, unknown)).closedDays()).as("값=%s", unknown).isNull();
		}
	}

	@Test
	@DisplayName("#172-S8 위도·경도가 비어 있으면 좌표 없음으로 제외한다")
	void noCoordinatesIsExcluded() {
		assertThat(ShelterPlaceFactory.create(record("", "", "09:00", "18:00", null)).exclusion())
			.isEqualTo(LocalDataExclusion.NO_COORDINATES);
		assertThat(ShelterPlaceFactory.create(record(" ", "128.5", "09:00", "18:00", null)).exclusion())
			.isEqualTo(LocalDataExclusion.NO_COORDINATES);
	}

	@Test
	@DisplayName("#172-S8 숫자가 아니거나 한국 범위 밖(바다 한가운데, 0, 위경도를 바꾼 값)이면 범위 밖으로 제외하고 좌표를 고치지 않는다")
	void outOfRangeIsExcluded() {
		for (String[] bad : new String[][] { { "abc", "128.5" }, { "0", "0" }, { "128.5", "35.9" }, { "NaN", "128.5" },
				{ "35.9", "Infinity" }, { "33.2", "124.85" }, { "45", "128.5" } }) {
			assertThat(ShelterPlaceFactory.create(record(bad[0], bad[1], "09:00", "18:00", null)).exclusion())
				.as("%s,%s", bad[0], bad[1])
				.isEqualTo(LocalDataExclusion.OUT_OF_RANGE);
		}
	}

	@Test
	@DisplayName("#172-S8 전화번호는 걸 수 있는 번호만 남기고, 읽을 수 없는 기준일은 센터를 버리지 않고 null로 둔다")
	void phoneAndStandardDate() {
		var weird = new ShelterRecord("a", "센터", "서울특별시 종로구 1", "전화 없음", "37.5", "127.0", null, null, null, "어제");

		PlaceDraft draft = draft(weird);

		assertThat(draft.phone()).isNull();
		assertThat(draft.sourceUpdatedAt()).isNull();
	}

	@Test
	@DisplayName("#172-S9 필수 항목이 빠진 행으로는 장소를 만들지 않는다(호출한 쪽이 isComplete로 먼저 거른다)")
	void incompleteRecordIsABug() {
		var missingName = new ShelterRecord("a", null, null, null, "37.5", "127.0", null, null, null, null);

		assertThat(missingName.isComplete()).isFalse();
		assertThatThrownBy(() -> ShelterPlaceFactory.create(missingName)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("#172-S9 isComplete: 등록번호·이름은 값이 있어야 하고, 위도·경도는 항목이 없거나 비어 있어도 된다(좌표 없음으로 제외한다)")
	void isCompleteRules() {
		assertThat(new ShelterRecord("a", "센터", null, null, "", "", null, null, null, null).isComplete()).isTrue();
		assertThat(new ShelterRecord(" ", "센터", null, null, "1", "1", null, null, null, null).isComplete()).isFalse();
		assertThat(new ShelterRecord("a", "", null, null, "1", "1", null, null, null, null).isComplete()).isFalse();
		assertThat(new ShelterRecord("a", "센터", null, null, null, "1", null, null, null, null).isComplete()).isTrue();
		assertThat(new ShelterRecord("a", "센터", null, null, "1", null, null, null, null, null).isComplete()).isTrue();
	}

	@Test
	@DisplayName("#172-S9 위도·경도 항목이 없는 센터는 좌표 없음으로 제외한다")
	void missingCoordinateFieldsAreExcluded() {
		var outcome = ShelterPlaceFactory.create(new ShelterRecord("a", "센터", null, null, null, null, null, null, null, null));

		assertThat(outcome.excluded()).isTrue();
		assertThat(outcome.exclusion()).isEqualTo(com.pawmento.map.domain.LocalDataExclusion.NO_COORDINATES);
	}

}
