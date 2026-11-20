package com.pawmento.map.application.exception;

public final class DiaryLocationContractViolation extends IllegalStateException {
	public DiaryLocationContractViolation() {
		super("동일 revision의 다른 내용 또는 일기 위치 식별자 계약 위반입니다.");
	}
}
