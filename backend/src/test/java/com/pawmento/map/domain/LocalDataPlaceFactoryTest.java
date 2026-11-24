package com.pawmento.map.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.offset;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 시나리오는 docs/map/animal-places.md(#172-S1, S3, S6, S7)에 있다. 인허가 행 한 줄을 장소로 바꾸는 규칙을 DB·네트워크 없이 확인한다
class LocalDataPlaceFactoryTest {

	private static final String ACTIVE = "영업/정상";

	// 대학로동물병원의 인허가 좌표(위도 37.584648, 경도 127.000190)
	private static final String X = "199947.178659";
	private static final String Y = "453593.826987";

	private static LocalDataRecord record(String status, String name, String roadAddress, String lotAddress,
			String phone, String x, String y, String updatedAt) {
		return new LocalDataRecord("MNG-1", status, name, roadAddress, lotAddress, phone, x, y, updatedAt);
	}

	private static LocalDataRecord normal() {
		return record(ACTIVE, "대학로동물병원", "서울특별시 종로구 대학로 1", "서울특별시 종로구 동숭동 1", "0222733233", X, Y,
				"2026-11-21 21:31:41");
	}

	@Test
	@DisplayName("#172-S1 영업/정상이고 좌표가 있는 행은 장소가 되고, 이름·주소·전화·좌표·출처 시각이 채워진다")
	void activeRecordBecomesPlace() {
		var outcome = LocalDataPlaceFactory.create(PlaceCategory.HOSPITAL, normal());

		assertThat(outcome.excluded()).isFalse();
		PlaceDraft draft = outcome.draft();
		assertThat(draft.category()).isEqualTo(PlaceCategory.HOSPITAL);
		assertThat(draft.sourceId()).isEqualTo("MNG-1");
		assertThat(draft.name()).isEqualTo("대학로동물병원");
		assertThat(draft.address()).isEqualTo("서울특별시 종로구 대학로 1");
		assertThat(draft.phone()).isEqualTo("02-2273-3233");
		assertThat(draft.latitude()).isCloseTo(37.584648, offset(0.0001));
		assertThat(draft.longitude()).isCloseTo(127.000190, offset(0.0001));
		assertThat(draft.open24h()).isFalse();
		assertThat(draft.open24hBasis()).isNull();
		assertThat(draft.openingHours()).isNull();
		// 병원·미용 원본에는 휴무 정보가 없다. 연중무휴가 아니라 정보 없음이다
		assertThat(draft.closedDays()).isNull();
		// 원본의 갱신 시각은 한국 시각이다
		assertThat(draft.sourceUpdatedAt()).isEqualTo(Instant.parse("2026-11-21T12:31:41Z"));
	}

	@Test
	@DisplayName("#172-S1 영업/정상이 아니거나 좌표가 비어 있으면 장소가 되지 않고 사유가 나온다")
	void excludedRecords() {
		var closed = LocalDataPlaceFactory.create(PlaceCategory.HOSPITAL,
				record("폐업", "폐업병원", "주소", null, null, X, Y, null));
		var noCoordinates = LocalDataPlaceFactory.create(PlaceCategory.HOSPITAL,
				record(ACTIVE, "좌표없는병원", "주소", null, null, "", "", null));
		var blankCoordinates = LocalDataPlaceFactory.create(PlaceCategory.HOSPITAL,
				record(ACTIVE, "좌표공백병원", "주소", null, null, "  ", " ", null));

		assertThat(closed.excluded()).isTrue();
		assertThat(closed.exclusion()).isEqualTo(LocalDataExclusion.CLOSED);
		assertThat(noCoordinates.exclusion()).isEqualTo(LocalDataExclusion.NO_COORDINATES);
		assertThat(blankCoordinates.exclusion()).isEqualTo(LocalDataExclusion.NO_COORDINATES);
		assertThat(closed.draft()).isNull();
	}

	@Test
	@DisplayName("#172-S3 X=-327.2, Y=-30310.5처럼 범위 밖이거나 숫자가 아닌 좌표는 제외하고 좌표를 고치지 않는다")
	void outOfRangeCoordinates() {
		var invalid = LocalDataPlaceFactory.create(PlaceCategory.HOSPITAL,
				record(ACTIVE, "이상한좌표병원", "주소", null, null, "-327.2", "-30310.5", null));
		var zero = LocalDataPlaceFactory.create(PlaceCategory.HOSPITAL,
				record(ACTIVE, "영점병원", "주소", null, null, "0", "0", null));
		var notNumber = LocalDataPlaceFactory.create(PlaceCategory.HOSPITAL,
				record(ACTIVE, "문자좌표병원", "주소", null, null, "abc", "123", null));
		var notFinite = LocalDataPlaceFactory.create(PlaceCategory.HOSPITAL,
				record(ACTIVE, "무한좌표병원", "주소", null, null, "NaN", "Infinity", null));

		assertThat(invalid.exclusion()).isEqualTo(LocalDataExclusion.OUT_OF_RANGE);
		assertThat(zero.exclusion()).isEqualTo(LocalDataExclusion.OUT_OF_RANGE);
		assertThat(notNumber.exclusion()).isEqualTo(LocalDataExclusion.OUT_OF_RANGE);
		assertThat(notFinite.exclusion()).isEqualTo(LocalDataExclusion.OUT_OF_RANGE);
	}

	@Test
	@DisplayName("#172-S6 전화번호는 정리 규칙대로 저장하고, 걸 수 없는 번호는 없음이다. 8자리는 서울 주소일 때만 02를 붙인다")
	void phoneRules() {
		var seoul = LocalDataPlaceFactory.create(PlaceCategory.HOSPITAL,
				record(ACTIVE, "서울병원", "서울특별시 종로구 1", null, "36728441", X, Y, null));
		var gyeonggi = LocalDataPlaceFactory.create(PlaceCategory.HOSPITAL,
				record(ACTIVE, "경기병원", "경기도 성남시 1", null, "36728441", X, Y, null));
		var empty = LocalDataPlaceFactory.create(PlaceCategory.HOSPITAL,
				record(ACTIVE, "전화없는병원", "서울특별시 종로구 1", null, "", X, Y, null));
		var extension = LocalDataPlaceFactory.create(PlaceCategory.HOSPITAL,
				record(ACTIVE, "내선병원", "서울특별시 종로구 1", null, "02-733-9006 (내선 1)", X, Y, null));

		assertThat(seoul.draft().phone()).isEqualTo("02-3672-8441");
		assertThat(gyeonggi.draft().phone()).isNull();
		assertThat(empty.draft().phone()).isNull();
		assertThat(extension.draft().phone()).isNull();
	}

	@Test
	@DisplayName("#172-S7 이름에 24시가 있는 병원은 open24h=true, 근거 NAME이고, 진월24동물병원은 false다")
	void open24hByName() {
		var twentyFour = LocalDataPlaceFactory.create(PlaceCategory.HOSPITAL,
				record(ACTIVE, "24시 해든동물병원", "서울특별시 종로구 1", null, null, X, Y, null));
		var notTwentyFour = LocalDataPlaceFactory.create(PlaceCategory.HOSPITAL,
				record(ACTIVE, "진월24동물병원", "서울특별시 종로구 1", null, null, X, Y, null));

		assertThat(twentyFour.draft().open24h()).isTrue();
		assertThat(twentyFour.draft().open24hBasis()).isEqualTo(Open24hBasis.NAME);
		assertThat(notTwentyFour.draft().open24h()).isFalse();
		assertThat(notTwentyFour.draft().open24hBasis()).isNull();
	}

	@Test
	@DisplayName("#172-S7 이름의 24시 판단은 병원에만 쓴다. 미용은 이름에 24시가 있어도 24시로 보지 않는다(실제 데이터로 확인하지 못했다)")
	void open24hByNameAppliesOnlyToHospitals() {
		var grooming = LocalDataPlaceFactory.create(PlaceCategory.GROOMING,
				record(ACTIVE, "24시 펫미용", "서울특별시 종로구 1", null, null, X, Y, null));

		assertThat(grooming.excluded()).isFalse();
		assertThat(grooming.draft().open24h()).isFalse();
		assertThat(grooming.draft().open24hBasis()).isNull();
	}

	@Test
	@DisplayName("#172-S1 미용도 같은 규칙으로 GROOMING 장소가 된다")
	void groomingUsesTheSameRules() {
		var outcome = LocalDataPlaceFactory.create(PlaceCategory.GROOMING, normal());

		assertThat(outcome.draft().category()).isEqualTo(PlaceCategory.GROOMING);
	}

	@Test
	@DisplayName("#172-S1 도로명 주소가 없으면 지번 주소를 쓰고, 둘 다 없으면 주소 없음이다. 가려진(*) 주소는 그대로 둔다")
	void addressFallback() {
		var lot = LocalDataPlaceFactory.create(PlaceCategory.GROOMING,
				record(ACTIVE, "미용", "", "광주 북구 운암동 *** 운암프라자", null, X, Y, null));
		var none = LocalDataPlaceFactory.create(PlaceCategory.GROOMING, record(ACTIVE, "미용", " ", null, null, X, Y, null));
		var masked = LocalDataPlaceFactory.create(PlaceCategory.GROOMING,
				record(ACTIVE, "미용", "광주 북구 서강로 ***-*, ***호", null, null, X, Y, null));

		assertThat(lot.draft().address()).isEqualTo("광주 북구 운암동 *** 운암프라자");
		assertThat(none.draft().address()).isNull();
		assertThat(masked.draft().address()).isEqualTo("광주 북구 서강로 ***-*, ***호");
	}

	@Test
	@DisplayName("#172-S1 갱신 시각이 비었거나 형식이 다르면 시각 없음으로 두고 장소는 저장한다")
	void unreadableUpdatedAt() {
		var blank = LocalDataPlaceFactory.create(PlaceCategory.HOSPITAL,
				record(ACTIVE, "병원", "주소", null, null, X, Y, ""));
		var broken = LocalDataPlaceFactory.create(PlaceCategory.HOSPITAL,
				record(ACTIVE, "병원", "주소", null, null, X, Y, "어제"));

		assertThat(blank.draft().sourceUpdatedAt()).isNull();
		assertThat(broken.draft().sourceUpdatedAt()).isNull();
	}

	@Test
	@DisplayName("#172-S5 필수 항목(관리번호·상태·이름·좌표 항목)이 빠졌거나 관리번호·이름이 비어 있는 행은 isComplete가 false이고, 만들려고 하면 거절한다")
	void incompleteRecords() {
		assertThat(normal().isComplete()).isTrue();
		assertThat(record(ACTIVE, "병원", "주소", null, null, X, Y, null).isComplete()).isTrue();
		for (LocalDataRecord incomplete : new LocalDataRecord[] {
				new LocalDataRecord(null, ACTIVE, "병원", "주소", null, null, X, Y, null),
				new LocalDataRecord("MNG-1", null, "병원", "주소", null, null, X, Y, null),
				new LocalDataRecord("MNG-1", ACTIVE, null, "주소", null, null, X, Y, null),
				new LocalDataRecord("MNG-1", ACTIVE, "병원", "주소", null, null, null, Y, null),
				new LocalDataRecord("MNG-1", ACTIVE, "병원", "주소", null, null, X, null, null),
				new LocalDataRecord("", ACTIVE, "병원", "주소", null, null, X, Y, null),
				new LocalDataRecord("  ", ACTIVE, "병원", "주소", null, null, X, Y, null),
				new LocalDataRecord("MNG-1", ACTIVE, "", "주소", null, null, X, Y, null),
				new LocalDataRecord("MNG-1", ACTIVE, "  ", "주소", null, null, X, Y, null) }) {
			assertThat(incomplete.isComplete()).isFalse();
			assertThatThrownBy(() -> LocalDataPlaceFactory.create(PlaceCategory.HOSPITAL, incomplete))
				.isInstanceOf(IllegalArgumentException.class);
		}
	}

	@Test
	@DisplayName("#172-S1 결과(Outcome)는 장소와 제외 이유 중 정확히 하나만 가진다. 둘 다 없거나 둘 다 있으면 만들 수 없다")
	void outcomeHasExactlyOneOfDraftAndExclusion() {
		PlaceDraft draft = LocalDataPlaceFactory.create(PlaceCategory.HOSPITAL, normal()).draft();

		assertThatThrownBy(() -> new LocalDataPlaceFactory.Outcome(null, null))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new LocalDataPlaceFactory.Outcome(draft, LocalDataExclusion.CLOSED))
			.isInstanceOf(IllegalArgumentException.class);
		assertThat(new LocalDataPlaceFactory.Outcome(draft, null).excluded()).isFalse();
		assertThat(new LocalDataPlaceFactory.Outcome(null, LocalDataExclusion.CLOSED).excluded()).isTrue();
	}

	@Test
	@DisplayName("#172-S1 보호센터 종류는 이 규칙으로 만들 수 없다(센터는 다른 API라 별도 규칙이다)")
	void shelterIsNotLocalData() {
		assertThatThrownBy(() -> LocalDataPlaceFactory.create(PlaceCategory.SHELTER, normal()))
			.isInstanceOf(IllegalArgumentException.class);
	}

}
