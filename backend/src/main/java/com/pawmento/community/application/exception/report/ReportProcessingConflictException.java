package com.pawmento.community.application.exception.report;

import com.pawmento.global.exception.StateConflictException;

public class ReportProcessingConflictException extends StateConflictException {
	public ReportProcessingConflictException(String message) {
		super(message);
	}
}
