package com.pawmento.map.domain;

/**
 * 인허가 데이터의 좌표(EPSG:5174, Bessel 1841 타원체의 중부원점 TM)를 위도·경도(WGS84)로 바꾼다.
 * ① 평면 좌표를 Bessel 타원체의 위도·경도로 되돌리고(역 횡메르카토르) ② 도쿄 측지계를 WGS84로 옮기는 7변수 변환을 적용한다.
 * ②를 빼면 약 375m 어긋나므로 반드시 적용한다. 대학로동물병원(X=199947.178659, Y=453593.826987)이 위도 37.584648,
 * 경도 127.000190에서 5m 이내인 것으로 확인했다(#172-S2).
 */
public final class KoreaTmConverter {

	// Bessel 1841 타원체
	private static final double BESSEL_A = 6_377_397.155;
	private static final double BESSEL_F = 1 / 299.1528128;
	private static final double BESSEL_E2 = BESSEL_F * (2 - BESSEL_F);

	// WGS84 타원체
	private static final double WGS84_A = 6_378_137.0;
	private static final double WGS84_F = 1 / 298.257223563;
	private static final double WGS84_E2 = WGS84_F * (2 - WGS84_F);

	// 중부원점 TM(EPSG:5174)
	private static final double LATITUDE_OF_ORIGIN = Math.toRadians(38);
	private static final double CENTRAL_MERIDIAN = Math.toRadians(127.0028902777778);
	private static final double SCALE = 1.0;
	private static final double FALSE_EASTING = 200_000;
	private static final double FALSE_NORTHING = 500_000;

	// 도쿄 → WGS84 (EPSG:5174가 쓰는 값). 이동(m), 회전(초), 축척(ppm). 회전은 position-vector 규약이다(coordinate-frame 규약과 회전 부호가 반대다)
	private static final double TX = -115.80;
	private static final double TY = 474.99;
	private static final double TZ = 674.11;
	private static final double ARC_SECOND = Math.PI / 180 / 3600;
	private static final double RX = 1.16 * ARC_SECOND;
	private static final double RY = -2.31 * ARC_SECOND;
	private static final double RZ = -1.63 * ARC_SECOND;
	private static final double SCALE_PPM = 6.43e-6;

	private KoreaTmConverter() {
	}

	/** 변환한 위도·경도(도). */
	public record Wgs84(double latitude, double longitude) {

		/**
		 * 대한민국 범위(위도 33~39, 경도 124~132) 안인지. 잘못된 좌표를 걸러내는 데 쓴다.
		 * X=-327, Y=-30310 같은 잘못된 값은 변환하면 제주 서남쪽 바다(위도 33.2, 경도 124.85)가 되어 상자 안에 들어오므로,
		 * 땅이 없는 그 구석(위도 33.9 미만이면서 경도 126.0 미만)도 뺀다. 제주 서쪽 끝은 경도 126.1이고 가거도는
		 * 위도 34.07이라 실제 장소는 빠지지 않는다.
		 */
		public boolean isInKorea() {
			boolean inBox = latitude >= 33 && latitude <= 39 && longitude >= 124 && longitude <= 132;
			boolean inSouthwestSea = latitude < 33.9 && longitude < 126.0;
			return inBox && !inSouthwestSea;
		}
	}

	public static Wgs84 toWgs84(double x, double y) {
		if (!Double.isFinite(x) || !Double.isFinite(y)) {
			throw new IllegalArgumentException("좌표는 유한한 숫자여야 합니다.");
		}
		double[] bessel = inverseTransverseMercator(x, y);
		return datumShiftToWgs84(bessel[0], bessel[1]);
	}

	// 평면 좌표(x, y) → Bessel 타원체의 위도·경도(라디안)
	private static double[] inverseTransverseMercator(double x, double y) {
		double e2 = BESSEL_E2;
		double ep2 = e2 / (1 - e2);
		double m0 = meridianArc(LATITUDE_OF_ORIGIN, e2);
		double m = m0 + (y - FALSE_NORTHING) / SCALE;

		double e1 = (1 - Math.sqrt(1 - e2)) / (1 + Math.sqrt(1 - e2));
		double mu = m / (BESSEL_A * (1 - e2 / 4 - 3 * e2 * e2 / 64 - 5 * e2 * e2 * e2 / 256));
		double phi1 = mu + (3 * e1 / 2 - 27 * Math.pow(e1, 3) / 32) * Math.sin(2 * mu)
				+ (21 * e1 * e1 / 16 - 55 * Math.pow(e1, 4) / 32) * Math.sin(4 * mu)
				+ (151 * Math.pow(e1, 3) / 96) * Math.sin(6 * mu)
				+ (1097 * Math.pow(e1, 4) / 512) * Math.sin(8 * mu);

		double sin1 = Math.sin(phi1);
		double cos1 = Math.cos(phi1);
		double tan1 = Math.tan(phi1);
		double c1 = ep2 * cos1 * cos1;
		double t1 = tan1 * tan1;
		double n1 = BESSEL_A / Math.sqrt(1 - e2 * sin1 * sin1);
		double r1 = BESSEL_A * (1 - e2) / Math.pow(1 - e2 * sin1 * sin1, 1.5);
		double d = (x - FALSE_EASTING) / (n1 * SCALE);

		double latitude = phi1 - (n1 * tan1 / r1) * (d * d / 2
				- (5 + 3 * t1 + 10 * c1 - 4 * c1 * c1 - 9 * ep2) * Math.pow(d, 4) / 24
				+ (61 + 90 * t1 + 298 * c1 + 45 * t1 * t1 - 252 * ep2 - 3 * c1 * c1) * Math.pow(d, 6) / 720);
		double longitude = CENTRAL_MERIDIAN + (d - (1 + 2 * t1 + c1) * Math.pow(d, 3) / 6
				+ (5 - 2 * c1 + 28 * t1 - 3 * c1 * c1 + 8 * ep2 + 24 * t1 * t1) * Math.pow(d, 5) / 120) / cos1;
		return new double[] { latitude, longitude };
	}

	// 적도에서 위도 phi까지의 자오선 호의 길이(m)
	private static double meridianArc(double phi, double e2) {
		double e4 = e2 * e2;
		double e6 = e4 * e2;
		return BESSEL_A * ((1 - e2 / 4 - 3 * e4 / 64 - 5 * e6 / 256) * phi
				- (3 * e2 / 8 + 3 * e4 / 32 + 45 * e6 / 1024) * Math.sin(2 * phi)
				+ (15 * e4 / 256 + 45 * e6 / 1024) * Math.sin(4 * phi) - (35 * e6 / 3072) * Math.sin(6 * phi));
	}

	// Bessel(도쿄) 위도·경도(라디안) → WGS84 위도·경도(도). 지구 중심 좌표로 바꿔 7변수 변환을 하고 다시 위도·경도로 되돌린다
	private static Wgs84 datumShiftToWgs84(double latitude, double longitude) {
		double sinLat = Math.sin(latitude);
		double cosLat = Math.cos(latitude);
		double n = BESSEL_A / Math.sqrt(1 - BESSEL_E2 * sinLat * sinLat);
		// 높이는 알 수 없어 0으로 둔다
		double x = n * cosLat * Math.cos(longitude);
		double y = n * cosLat * Math.sin(longitude);
		double z = n * (1 - BESSEL_E2) * sinLat;

		double x2 = TX + (1 + SCALE_PPM) * (x - RZ * y + RY * z);
		double y2 = TY + (1 + SCALE_PPM) * (RZ * x + y - RX * z);
		double z2 = TZ + (1 + SCALE_PPM) * (-RY * x + RX * y + z);

		double p = Math.hypot(x2, y2);
		double lng = Math.atan2(y2, x2);
		double lat = Math.atan2(z2, p * (1 - WGS84_E2));
		for (int i = 0; i < 10; i++) {
			double s = Math.sin(lat);
			double nn = WGS84_A / Math.sqrt(1 - WGS84_E2 * s * s);
			double next = Math.atan2(z2 + WGS84_E2 * nn * s, p);
			if (Math.abs(next - lat) < 1e-13) {
				lat = next;
				break;
			}
			lat = next;
		}
		return new Wgs84(Math.toDegrees(lat), Math.toDegrees(lng));
	}

}
