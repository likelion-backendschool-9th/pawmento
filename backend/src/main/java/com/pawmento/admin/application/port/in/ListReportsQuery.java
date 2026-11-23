package com.pawmento.admin.application.port.in;

public record ListReportsQuery(Status status, int size, Long beforeReportId) {
	public enum Status {
		PENDING,
		RESOLVED,
		DISMISSED
	}
}
