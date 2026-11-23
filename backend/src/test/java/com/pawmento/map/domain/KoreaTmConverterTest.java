package com.pawmento.map.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 시나리오는 docs/map/animal-places.md(#172-S2, S3)에 있다.
// 기준점의 TM 좌표는 PROJ(pyproj)로 위도·경도에서 EPSG:5174로 바꿔 만들었다(구현과 별개의 계산이다)
class KoreaTmConverterTest {

	private static final double MAX_ERROR_METERS = 5;

	// 두 좌표 사이의 거리(m). 하버사인 공식이다
	private static double meters(double latitude1, double longitude1, double latitude2, double longitude2) {
		double dLat = Math.toRadians(latitude2 - latitude1);
		double dLng = Math.toRadians(longitude2 - longitude1);
		double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(Math.toRadians(latitude1))
				* Math.cos(Math.toRadians(latitude2)) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
		return 2 * 6_371_000 * Math.asin(Math.sqrt(a));
	}

	private static void assertWithinMeters(KoreaTmConverter.Wgs84 actual, double latitude, double longitude) {
		assertThat(meters(actual.latitude(), actual.longitude(), latitude, longitude))
			.isLessThanOrEqualTo(MAX_ERROR_METERS);
	}

	@Test
	@DisplayName("#172-S2 대학로동물병원의 인허가 좌표는 위도 37.584648, 경도 127.000190에서 5m 이내다")
	void daehakro() {
		var actual = KoreaTmConverter.toWgs84(199947.178659, 453593.826987);

		assertWithinMeters(actual, 37.584648, 127.000190);
		// 도쿄 측지계 보정을 하지 않은 값(37.58185, 127.00229)이 아니다
		assertThat(meters(actual.latitude(), actual.longitude(), 37.58185, 127.00229))
			.isGreaterThan(300);
	}

	@Test
	@DisplayName("#172-S2 서울·부산·제주·강원의 기준점도 5m 이내다")
	void regionalReferencePoints() {
		assertWithinMeters(KoreaTmConverter.toWgs84(197977.90515902848, 451557.6553309477), 37.5663, 126.9779);
		assertWithinMeters(KoreaTmConverter.toWgs84(388949.0623496191, 188704.83496739058), 35.1798, 129.0750);
		assertWithinMeters(KoreaTmConverter.toWgs84(156361.1017044267, 450.9819068305078), 33.4996, 126.5312);
		assertWithinMeters(KoreaTmConverter.toWgs84(264135.23990045825, 486769.94709379383), 37.8813, 127.7298);
	}

	@Test
	@DisplayName("#172-S3 X=-327.2, Y=-30310.5 같은 값은 변환 결과가 대한민국 범위 밖이라 범위 검사에서 걸린다")
	void outOfRangeCoordinates() {
		assertThat(KoreaTmConverter.toWgs84(-327.2, -30310.5).isInKorea()).isFalse();
		assertThat(KoreaTmConverter.toWgs84(0, 0).isInKorea()).isFalse();
		assertThat(KoreaTmConverter.toWgs84(199947.178659, 453593.826987).isInKorea()).isTrue();
	}

	@Test
	@DisplayName("#172-S3 위도 33~39, 경도 124~132의 경계 안은 한국이고 밖은 아니다")
	void koreaBounds() {
		assertThat(new KoreaTmConverter.Wgs84(33, 127).isInKorea()).isTrue();
		assertThat(new KoreaTmConverter.Wgs84(33.9, 124).isInKorea()).isTrue();
		assertThat(new KoreaTmConverter.Wgs84(39, 132).isInKorea()).isTrue();
		assertThat(new KoreaTmConverter.Wgs84(32.999, 127).isInKorea()).isFalse();
		assertThat(new KoreaTmConverter.Wgs84(36, 123.999).isInKorea()).isFalse();
		assertThat(new KoreaTmConverter.Wgs84(39.001, 127).isInKorea()).isFalse();
		assertThat(new KoreaTmConverter.Wgs84(36, 132.001).isInKorea()).isFalse();
	}

	@Test
	@DisplayName("#172-S3 제주 서남쪽 바다는 빼고, 제주·가거도·백령도·독도는 한국으로 본다")
	void southwestSeaIsExcludedButIslandsAreKept() {
		assertThat(new KoreaTmConverter.Wgs84(33.20, 124.85).isInKorea()).isFalse();
		assertThat(new KoreaTmConverter.Wgs84(33.5, 125.9).isInKorea()).isFalse();
		// 제주 서쪽 끝(한림), 서귀포, 마라도, 가거도, 백령도, 독도
		assertThat(new KoreaTmConverter.Wgs84(33.41, 126.14).isInKorea()).isTrue();
		assertThat(new KoreaTmConverter.Wgs84(33.25, 126.56).isInKorea()).isTrue();
		assertThat(new KoreaTmConverter.Wgs84(33.11, 126.27).isInKorea()).isTrue();
		assertThat(new KoreaTmConverter.Wgs84(34.07, 125.12).isInKorea()).isTrue();
		assertThat(new KoreaTmConverter.Wgs84(37.96, 124.70).isInKorea()).isTrue();
		assertThat(new KoreaTmConverter.Wgs84(37.24, 131.87).isInKorea()).isTrue();
	}

	@Test
	@DisplayName("#172-S3 숫자가 아닌 값(NaN, 무한대)은 거절한다")
	void rejectsNonFinite() {
		assertThatThrownBy(() -> KoreaTmConverter.toWgs84(Double.NaN, 1)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> KoreaTmConverter.toWgs84(1, Double.POSITIVE_INFINITY))
			.isInstanceOf(IllegalArgumentException.class);
	}

}
