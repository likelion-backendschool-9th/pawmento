package com.pawmento.community.adapter.in.web.report.exception;

import com.pawmento.global.exception.RuleViolationException;

public class InvalidReportProcessingRequestException extends RuleViolationException {
	public InvalidReportProcessingRequestException(String message) {
		super(message);
	}
}
