package com.pawmento.map.application.exception;

public final class DiaryLocationContractViolation extends IllegalStateException {
	public DiaryLocationContractViolation() {
		super("동일 revision의 다른 내용 또는 일기 위치 식별자 계약 위반입니다.");
	}
	public DiaryLocationContractViolation(Throwable cause) {
		super("일기 위치 내부 계약을 위반했습니다.");
		// MVC는 cause의 RuleViolationException을 따라가면 400으로 응답한다.
		// 내부 검증 실패는 미처리 서버 오류로 남기고 진단 정보는 suppressed로 보존한다.
		addSuppressed(cause);
	}
}
