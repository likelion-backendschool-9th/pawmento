package com.pawmento.community.adapter.in.web.report.exception;

import com.pawmento.global.exception.RuleViolationException;

public class InvalidReportListRequestException extends RuleViolationException {
	public enum Field {
		STATUS("status는 PENDING, RESOLVED, DISMISSED 중 하나여야 해요."),
		SIZE("size는 1 이상 50 이하의 정수여야 해요."),
		CURSOR("cursor는 양의 정수 신고 ID여야 해요.");

		private final String message;

		Field(String message) {
			this.message = message;
		}
	}

	public InvalidReportListRequestException(Field field) {
		super(field.message);
	}

	public InvalidReportListRequestException(Field field, Throwable cause) {
		super(field.message, cause);
	}
}
