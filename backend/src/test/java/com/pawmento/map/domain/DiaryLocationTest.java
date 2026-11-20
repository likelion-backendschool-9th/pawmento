package com.pawmento.map.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.pawmento.map.domain.exception.InvalidCoordinatesException;
import com.pawmento.map.domain.exception.InvalidDiaryLocationException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

// 시나리오는 docs/map/record-location.md에 있다. 값의 범위와 규칙은 도메인이 마지막으로 지킨다
class DiaryLocationTest {

	private static DiaryLocation location(double latitude, double longitude, String placeName) {
		return DiaryLocation.create(1L, 2L, 3L, latitude, longitude, placeName);
	}

	@Test
	@DisplayName("#19-S1 위치를 만들면 값이 그대로 담기고 id는 아직 없다")
	void create() {
		DiaryLocation location = location(37.4979, 127.0276, "강남역");

		assertThat(location.getId()).isNull();
		assertThat(location.getDiaryId()).isEqualTo(1L);
		assertThat(location.getPetId()).isEqualTo(2L);
		assertThat(location.getCreatedBy()).isEqualTo(3L);
		assertThat(location.getLatitude()).isEqualTo(37.4979);
		assertThat(location.getLongitude()).isEqualTo(127.0276);
		assertThat(location.getPlaceName()).isEqualTo("강남역");
	}

	@ParameterizedTest
	@ValueSource(doubles = { -90.0, 0.0, 90.0 })
	@DisplayName("#19-S2 위도는 -90과 90 경계값까지 받는다")
	void acceptsLatitudeBoundary(double latitude) {
		assertThat(location(latitude, 0.0, null).getLatitude()).isEqualTo(latitude);
	}

	@ParameterizedTest
	@ValueSource(doubles = { -180.0, 0.0, 180.0 })
	@DisplayName("#19-S2 경도는 -180과 180 경계값까지 받는다")
	void acceptsLongitudeBoundary(double longitude) {
		assertThat(location(0.0, longitude, null).getLongitude()).isEqualTo(longitude);
	}

	@ParameterizedTest
	@ValueSource(doubles = { -90.0001, 90.0001, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY })
	@DisplayName("#19-S2 위도가 범위를 벗어나거나 숫자가 아니면 만들 수 없다")
	void rejectsInvalidLatitude(double latitude) {
		assertThatThrownBy(() -> location(latitude, 0.0, null)).isInstanceOf(InvalidCoordinatesException.class)
			.hasMessageContaining("위도");
	}

	@ParameterizedTest
	@ValueSource(doubles = { -180.0001, 180.0001, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY })
	@DisplayName("#19-S2 경도가 범위를 벗어나거나 숫자가 아니면 만들 수 없다")
	void rejectsInvalidLongitude(double longitude) {
		assertThatThrownBy(() -> location(0.0, longitude, null)).isInstanceOf(InvalidCoordinatesException.class)
			.hasMessageContaining("경도");
	}

	@Test
	@DisplayName("#19-S7 기록, 반려동물, 작성자 중 하나라도 없으면 만들 수 없다")
	void requiresOwnerIds() {
		assertThatThrownBy(() -> DiaryLocation.create(null, 2L, 3L, 0.0, 0.0, null))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> DiaryLocation.create(1L, null, 3L, 0.0, 0.0, null))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> DiaryLocation.create(1L, 2L, null, 0.0, 0.0, null))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("#19-S1 장소 이름은 앞뒤 공백을 떼고, 비었거나 공백뿐이면 없는 것으로 본다")
	void normalizesPlaceName() {
		assertThat(location(0.0, 0.0, "  한강공원  ").getPlaceName()).isEqualTo("한강공원");
		assertThat(location(0.0, 0.0, "").getPlaceName()).isNull();
		assertThat(location(0.0, 0.0, "   ").getPlaceName()).isNull();
		assertThat(location(0.0, 0.0, null).getPlaceName()).isNull();
	}

	@Test
	@DisplayName("#19-S1 장소 이름은 255자까지 받고 넘으면 만들 수 없다")
	void limitsPlaceNameLength() {
		assertThat(location(0.0, 0.0, "가".repeat(255)).getPlaceName()).hasSize(255);
		assertThatThrownBy(() -> location(0.0, 0.0, "가".repeat(256))).isInstanceOf(InvalidDiaryLocationException.class)
			.hasMessageContaining("255");
	}

	@Test
	@DisplayName("#19-S1 같은 기록의 위치를 바꾸면 좌표와 장소 이름이 바뀌고 기록·작성자는 그대로다")
	void move() {
		DiaryLocation location = DiaryLocation.restore(10L, 1L, 2L, 3L, 37.0, 127.0, "이전", LocationSource.MANUAL, null, 0);

		location.move(35.1796, 129.0756, "부산");

		assertThat(location.getId()).isEqualTo(10L);
		assertThat(location.getDiaryId()).isEqualTo(1L);
		assertThat(location.getCreatedBy()).isEqualTo(3L);
		assertThat(location.getLatitude()).isEqualTo(35.1796);
		assertThat(location.getLongitude()).isEqualTo(129.0756);
		assertThat(location.getPlaceName()).isEqualTo("부산");
	}

	@Test
	@DisplayName("#19-S2 바꾸는 값이 규칙에 어긋나면 아무것도 바뀌지 않는다")
	void moveKeepsValuesOnFailure() {
		DiaryLocation location = DiaryLocation.restore(10L, 1L, 2L, 3L, 37.0, 127.0, "이전", LocationSource.MANUAL, null, 0);

		assertThatThrownBy(() -> location.move(35.0, 129.0, "가".repeat(256)))
			.isInstanceOf(InvalidDiaryLocationException.class);
		assertThatThrownBy(() -> location.move(91.0, 129.0, "새 이름")).isInstanceOf(InvalidCoordinatesException.class);

		assertThat(location.getLatitude()).isEqualTo(37.0);
		assertThat(location.getLongitude()).isEqualTo(127.0);
		assertThat(location.getPlaceName()).isEqualTo("이전");
	}

	@Test
	@DisplayName("#19-S1 저장된 값을 되살릴 때도 같은 규칙을 검사하고 id가 필요하다")
	void restoreValidates() {
		assertThatThrownBy(() -> DiaryLocation.restore(null, 1L, 2L, 3L, 0.0, 0.0, null, LocationSource.MANUAL, null, 0))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> DiaryLocation.restore(1L, 1L, 2L, 3L, 100.0, 0.0, null, LocationSource.MANUAL, null, 0))
			.isInstanceOf(InvalidCoordinatesException.class);
	}


	@Test
	@DisplayName("#44-S6 새 위치는 직접 고른 위치(MANUAL)이고 출처 자산이 없으며 revision 0에서 시작한다")
	void createStartsAsManualWithRevisionZero() {
		DiaryLocation location = DiaryLocation.create(1L, 2L, 3L, 37.0, 127.0, null);

		assertThat(location.getSource()).isEqualTo(LocationSource.MANUAL);
		assertThat(location.getSourceAssetId()).isNull();
		assertThat(location.getSourceRevision()).isZero();
	}

	@Test
	@DisplayName("#44-S6 저장된 값으로 다시 만들면 출처와 revision을 그대로 가진다")
	void restoreKeepsSourceAndRevision() {
		DiaryLocation location = DiaryLocation.restore(10L, 1L, 2L, 3L, 37.0, 127.0, null, LocationSource.MANUAL, null,
				7);

		assertThat(location.getSource()).isEqualTo(LocationSource.MANUAL);
		assertThat(location.getSourceAssetId()).isNull();
		assertThat(location.getSourceRevision()).isEqualTo(7L);
	}

	@Test
	@DisplayName("#44-S6 출처가 없거나 직접 고른 위치에 자산이 있거나 revision이 음수이면 프로그래밍 오류로 거절한다")
	void sourceInvariants() {
		assertThatThrownBy(() -> DiaryLocation.restore(10L, 1L, 2L, 3L, 37.0, 127.0, null, null, null, 0))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(
				() -> DiaryLocation.restore(10L, 1L, 2L, 3L, 37.0, 127.0, null, LocationSource.MANUAL, 5L, 0))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(
				() -> DiaryLocation.restore(10L, 1L, 2L, 3L, 37.0, 127.0, null, LocationSource.MANUAL, null, -1))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("#44-S6 위치를 옮겨도 출처와 revision은 바뀌지 않는다")
	void moveKeepsSourceAndRevision() {
		DiaryLocation location = DiaryLocation.restore(10L, 1L, 2L, 3L, 37.0, 127.0, "이전", LocationSource.MANUAL, null,
				4);

		location.move(36.0, 128.0, "새 이름");

		assertThat(location.getSource()).isEqualTo(LocationSource.MANUAL);
		assertThat(location.getSourceRevision()).isEqualTo(4L);
	}

	@Test
	@DisplayName("#44-S6 도메인이 최초 반영·재전송·오래된 revision·계약 위반을 판정한다")
	void decidesIncomingRevision() {
		var value = new DiaryLocationValue(37, 127, "공원");
		var current =
				new DiaryLocationState(
						10L, 1L, 2L, 3L, 37, 127, "공원", LocationSource.MANUAL, null, 7);
		assertThat(DiaryLocation.assess(null, 2L, 3L, 0, value))
				.isEqualTo(DiaryLocation.ApplyDecision.APPLIED);
		assertThat(DiaryLocation.assess(null, 2L, 3L, 0, null))
				.isEqualTo(DiaryLocation.ApplyDecision.DUPLICATE);
		assertThat(DiaryLocation.assess(current, 2L, 3L, 6, value))
				.isEqualTo(DiaryLocation.ApplyDecision.STALE);
		assertThat(DiaryLocation.assess(current, 2L, 3L, 7, value))
				.isEqualTo(DiaryLocation.ApplyDecision.DUPLICATE);
		assertThat(DiaryLocation.assess(current, 2L, 3L, 8, value))
				.isEqualTo(DiaryLocation.ApplyDecision.APPLIED);
		assertThat(DiaryLocation.assess(current, 2L, 3L, 8, null))
				.isEqualTo(DiaryLocation.ApplyDecision.APPLIED);
		assertThat(DiaryLocation.assess(current, 2L, 3L, 7, null))
				.isEqualTo(DiaryLocation.ApplyDecision.CONTRACT_VIOLATION);
		assertThat(DiaryLocation.assess(current, 2L, 3L, 7, new DiaryLocationValue(38, 127, "공원")))
				.isEqualTo(DiaryLocation.ApplyDecision.CONTRACT_VIOLATION);
		assertThat(DiaryLocation.assess(current, 20L, 3L, 8, value))
				.isEqualTo(DiaryLocation.ApplyDecision.CONTRACT_VIOLATION);
		assertThat(DiaryLocation.assess(current, 2L, 30L, 6, value))
				.isEqualTo(DiaryLocation.ApplyDecision.CONTRACT_VIOLATION);
		var corrupt =
				new DiaryLocationState(
						10L, 1L, 2L, 3L, 37, 127, "공원", LocationSource.MANUAL, 99L, 7);
		assertThat(DiaryLocation.assess(corrupt, 2L, 3L, 7, value))
				.isEqualTo(DiaryLocation.ApplyDecision.CONTRACT_VIOLATION);
	}

	@Test
	@DisplayName("#44-S6 입력 포트의 위치 값도 기존 도메인 좌표·장소명 규칙을 적용한다")
	void inputValueUsesDomainPolicy() {
		assertThat(new DiaryLocationValue(-0.0, 180, "  공원  "))
				.isEqualTo(new DiaryLocationValue(0.0, 180, "공원"));
		assertThat(new DiaryLocationValue(-90, -180, " ").placeName()).isNull();
		assertThatThrownBy(() -> new DiaryLocationValue(Double.NaN, 0, null))
				.isInstanceOf(InvalidCoordinatesException.class);
		assertThatThrownBy(() -> new DiaryLocationValue(0, 181, null))
				.isInstanceOf(InvalidCoordinatesException.class);
		assertThatThrownBy(() -> new DiaryLocationValue(0, 0, "가".repeat(256)))
				.isInstanceOf(InvalidDiaryLocationException.class);
	}
	@Test
	@DisplayName("#44-S6 명시적 정정도 관측 이후 바뀐 행은 STALE이고 같은 관측값만 반영한다")
	void decidesReconciliation() {
		var value = new DiaryLocationValue(37, 127, "공원");
		var observed = new DiaryLocationState(10L, 1L, 2L, 3L, 37, 127, "공원", LocationSource.MANUAL, null, 7);
		var changed = new DiaryLocationState(10L, 1L, 2L, 3L, 37, 127, "공원", LocationSource.MANUAL, null, 8);
		assertThat(DiaryLocation.assessReconciliation(changed, observed, value)).isEqualTo(DiaryLocation.ApplyDecision.STALE);
		assertThat(DiaryLocation.assessReconciliation(null, observed, null)).isEqualTo(DiaryLocation.ApplyDecision.STALE);
		assertThat(DiaryLocation.assessReconciliation(observed, observed, null)).isEqualTo(DiaryLocation.ApplyDecision.APPLIED);
		assertThat(DiaryLocation.assessReconciliation(null, null, null)).isEqualTo(DiaryLocation.ApplyDecision.DUPLICATE);
		assertThat(DiaryLocation.assessReconciliation(null, null, value)).isEqualTo(DiaryLocation.ApplyDecision.APPLIED);
	}

}
