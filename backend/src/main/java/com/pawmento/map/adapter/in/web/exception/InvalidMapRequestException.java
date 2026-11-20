package com.pawmento.map.adapter.in.web.exception;

import com.pawmento.global.exception.RuleViolationException;

// 지도 API의 쿼리 문자열이 규칙에 맞지 않는다(없음, 중복, 형식 오류). 웹 입력 오류라서 웹 어댑터에 두고 응답은 400이다
public class InvalidMapRequestException extends RuleViolationException {

	private InvalidMapRequestException(String message) {
		super(message);
	}

	public static InvalidMapRequestException missing(String name) {
		return new InvalidMapRequestException(name + "를 입력해 주세요.");
	}

	public static InvalidMapRequestException duplicated(String name) {
		return new InvalidMapRequestException(name + "는 한 번만 보내 주세요.");
	}

	public static InvalidMapRequestException invalidNumber(String name) {
		return new InvalidMapRequestException(name + "는 숫자로 입력해 주세요.");
	}

	public static InvalidMapRequestException invalidPositiveInteger(String name) {
		return new InvalidMapRequestException(name + "는 1 이상의 정수로 입력해 주세요.");
	}

	public static InvalidMapRequestException invalidDate(String name) {
		return new InvalidMapRequestException(name + "는 YYYY-MM-DD 형식으로 입력해 주세요.");
	}

	// 도메인의 영역 규칙(MapArea)과 같은 문구. 웹 어댑터는 도메인을 import하지 않아 문구를 따로 둔다
	public static InvalidMapRequestException areaReversed() {
		return new InvalidMapRequestException("영역의 최솟값은 최댓값보다 클 수 없습니다.");
	}

	public static InvalidMapRequestException latitudeOutOfRange() {
		return new InvalidMapRequestException(CoordinateRange.LATITUDE_MESSAGE);
	}

	public static InvalidMapRequestException longitudeOutOfRange() {
		return new InvalidMapRequestException(CoordinateRange.LONGITUDE_MESSAGE);
	}

	public static InvalidMapRequestException malformedEncoding() {
		return new InvalidMapRequestException("쿼리 문자열의 인코딩이 올바르지 않습니다.");
	}

}
