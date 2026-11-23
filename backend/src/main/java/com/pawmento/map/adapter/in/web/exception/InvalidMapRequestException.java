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

	public static InvalidMapRequestException invalidCategories(String name) {
		return new InvalidMapRequestException(
				name + "는 HOSPITAL, GROOMING, SHELTER 중에서 쉼표로 구분해 한 번씩만 입력해 주세요.");
	}

	public static InvalidMapRequestException invalidSyncCategory(String name) {
		return new InvalidMapRequestException(name + "는 HOSPITAL, GROOMING, SHELTER 중 하나만 입력해 주세요.");
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
