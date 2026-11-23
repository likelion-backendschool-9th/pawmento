package com.pawmento.admin.application.exception;

import com.pawmento.global.exception.StateConflictException;

public class ReportProcessingConflictException extends StateConflictException {
	public ReportProcessingConflictException(String message) {
		super(message);
	}
}
