package com.pawmento.report.domain.exception;

import com.pawmento.global.exception.RuleViolationException;

// 신고의 입력 규칙(신고자, 대상, 유형, 기타 설명)을 어겼다
public class InvalidReportException extends RuleViolationException {
	public InvalidReportException(String message) {
		super(message);
	}
}
