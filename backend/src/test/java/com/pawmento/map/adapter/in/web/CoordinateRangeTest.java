package com.pawmento.map.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.pawmento.map.adapter.in.web.AddWalkPointsRequest.PointRequest;
import com.pawmento.map.adapter.in.web.exception.CoordinateRange;
import com.pawmento.map.adapter.in.web.exception.InvalidMapRequestException;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

// 핀 조회(쿼리 문자열을 직접 해석)와 산책 좌표(JSON의 Bean Validation)가 위도·경도의 범위와 오류 문구를 어긋나지 않게 쓰는지 확인한다
class CoordinateRangeTest {

	private static final Instant AT = Instant.parse("2026-11-17T01:00:01Z");

	private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

	private Set<ConstraintViolation<PointRequest>> validate(double latitude, double longitude) {
		return validator.validate(new PointRequest(latitude, longitude, AT));
	}

	@Test
	@DisplayName("#19-S5 범위와 오류 문구의 값이 한 곳(CoordinateRange)에 있고 기존 문구와 같다")
	void rangeAndMessages() {
		assertThat(CoordinateRange.MIN_LATITUDE_TEXT).isEqualTo("-90.0");
		assertThat(CoordinateRange.MAX_LATITUDE_TEXT).isEqualTo("90.0");
		assertThat(CoordinateRange.MIN_LONGITUDE_TEXT).isEqualTo("-180.0");
		assertThat(CoordinateRange.MAX_LONGITUDE_TEXT).isEqualTo("180.0");
		assertThat(CoordinateRange.LATITUDE_MESSAGE).isEqualTo("위도는 -90 이상 90 이하로 입력해 주세요.");
		assertThat(CoordinateRange.LONGITUDE_MESSAGE).isEqualTo("경도는 -180 이상 180 이하로 입력해 주세요.");
	}

	@Test
	@DisplayName("#19-S5 쿼리 해석과 Bean Validation이 같은 경계를 쓰고 같은 문구를 낸다. 경계값은 둘 다 통과하고 그 바로 바깥은 둘 다 거절한다")
	void bothValidationsAgree() {
		// 경계값
		assertThat(validate(90.0, 180.0)).isEmpty();
		assertThat(validate(-90.0, -180.0)).isEmpty();
		assertThat(MapQueryParameters.parse("lat=90&lng=180", "lat", "lng").latitude("lat")).isEqualByComparingTo("90");
		assertThat(MapQueryParameters.parse("lat=-90&lng=-180", "lat", "lng").longitude("lng")).isEqualByComparingTo("-180");

		// 바로 바깥
		for (double outside : new double[] { 90.0000001, -90.0000001 }) {
			assertThat(validate(outside, 0.0)).extracting(ConstraintViolation::getMessage)
				.containsExactly(InvalidMapRequestException.latitudeOutOfRange().getMessage());
			String query = "lat=" + outside;
			org.assertj.core.api.Assertions
				.assertThatThrownBy(() -> MapQueryParameters.parse(query, "lat").latitude("lat"))
				.hasMessage(CoordinateRange.LATITUDE_MESSAGE);
		}
		for (double outside : new double[] { 180.0000001, -180.0000001 }) {
			assertThat(validate(0.0, outside)).extracting(ConstraintViolation::getMessage)
				.containsExactly(InvalidMapRequestException.longitudeOutOfRange().getMessage());
			String query = "lng=" + outside;
			org.assertj.core.api.Assertions
				.assertThatThrownBy(() -> MapQueryParameters.parse(query, "lng").longitude("lng"))
				.hasMessage(CoordinateRange.LONGITUDE_MESSAGE);
		}
	}

}
