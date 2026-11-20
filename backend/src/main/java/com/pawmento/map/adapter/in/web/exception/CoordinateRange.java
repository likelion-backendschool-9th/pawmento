package com.pawmento.map.adapter.in.web.exception;

/**
 * 웹 입력의 위도·경도 범위와 오류 문구. 쿼리 문자열을 직접 해석하는 쪽(MapQueryParameters)과 JSON 요청의 Bean Validation
 * (@Latitude, @Longitude)이 같은 값과 문구를 쓰도록 한 곳에 둔다. CoordinateRangeTest는 이 두 웹 입력 경로의 경계값과 오류
 * 문구가 같은지만 확인한다. 웹 어댑터는 도메인을 import하지 않아서 도메인의 같은 규칙(Coordinates)과는 값이 따로 있고,
 * 그 일치는 이 테스트가 확인하지 않는다.
 */
public final class CoordinateRange {

	public static final int MAX_LATITUDE_DEGREES = 90;

	public static final int MAX_LONGITUDE_DEGREES = 180;

	// 어노테이션 값은 컴파일 시점 상수여야 해서 숫자에서 문자열을 만든다
	public static final String MIN_LATITUDE_TEXT = "-" + MAX_LATITUDE_DEGREES + ".0";

	public static final String MAX_LATITUDE_TEXT = MAX_LATITUDE_DEGREES + ".0";

	public static final String MIN_LONGITUDE_TEXT = "-" + MAX_LONGITUDE_DEGREES + ".0";

	public static final String MAX_LONGITUDE_TEXT = MAX_LONGITUDE_DEGREES + ".0";

	public static final String LATITUDE_MESSAGE = "위도는 -" + MAX_LATITUDE_DEGREES + " 이상 " + MAX_LATITUDE_DEGREES
			+ " 이하로 입력해 주세요.";

	public static final String LONGITUDE_MESSAGE = "경도는 -" + MAX_LONGITUDE_DEGREES + " 이상 " + MAX_LONGITUDE_DEGREES
			+ " 이하로 입력해 주세요.";

	private CoordinateRange() {
	}

}
