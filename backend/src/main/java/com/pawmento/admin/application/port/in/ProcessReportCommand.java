package com.pawmento.admin.application.port.in;

public record ProcessReportCommand(long reportId, Resolution resolution, long expectedVersion, String memo) {
	public enum Resolution {
		RESOLVED,
		DISMISSED
	}
}
