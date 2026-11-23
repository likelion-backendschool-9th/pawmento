package com.pawmento.admin.adapter.in.web.exception;

import com.pawmento.global.exception.RuleViolationException;

public class InvalidReportProcessingRequestException extends RuleViolationException {
	public InvalidReportProcessingRequestException(String message) {
		super(message);
	}
}
