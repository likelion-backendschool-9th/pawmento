package com.pawmento.map.domain.exception;

import com.pawmento.global.exception.RuleViolationException;

// 장소 검색어가 규칙에 맞지 않는다(너무 짧거나 길다, 비교할 글자가 없다). 응답은 400이고 이유를 담아 화면이 안내하게 한다
public class InvalidSearchQueryException extends RuleViolationException {

	private InvalidSearchQueryException(String message) {
		super(message);
	}

	public static InvalidSearchQueryException tooShort(int min) {
		return new InvalidSearchQueryException("검색어는 " + min + "자 이상 입력해 주세요.");
	}

	public static InvalidSearchQueryException tooLong(int max) {
		return new InvalidSearchQueryException("검색어는 " + max + "자 이하로 입력해 주세요.");
	}

}
