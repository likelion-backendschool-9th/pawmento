package com.pawmento.community.adapter.in.web.report.exception;

import com.pawmento.global.exception.RuleViolationException;

public class InvalidReportListRequestException extends RuleViolationException {
	public InvalidReportListRequestException(String message) {
		super(message);
	}

	public InvalidReportListRequestException(String message, Throwable cause) {
		super(message, cause);
	}
}
