package com.pawmento.map.domain.exception;

import com.pawmento.global.exception.RuleViolationException;

// 기록 위치의 입력 규칙(장소 이름 길이)을 어겼다
public class InvalidDiaryLocationException extends RuleViolationException {

	private InvalidDiaryLocationException(String message) {
		super(message);
	}

	public static InvalidDiaryLocationException invalidIdentity() {
		return new InvalidDiaryLocationException("일기, 반려동물, 작성자 ID는 양수이고 revision은 0 이상이어야 합니다.");
	}

	public static InvalidDiaryLocationException invalidObservation() {
		return new InvalidDiaryLocationException("다른 일기의 관측값입니다.");
	}

	public static InvalidDiaryLocationException placeNameTooLong(int maxLength) {
		return new InvalidDiaryLocationException("장소 이름은 " + maxLength + "자 이하여야 합니다.");
	}
}
