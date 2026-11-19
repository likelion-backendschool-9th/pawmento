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

	public static InvalidMapRequestException invalid(String name, String expected) {
		return new InvalidMapRequestException(name + "는 " + expected + " 입력해 주세요.");
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
