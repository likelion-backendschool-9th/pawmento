package com.pawmento.community.application.port.in.report;

public record ProcessReportCommand(long administratorId, long reportId, Resolution resolution, long expectedVersion,
		String memo) {
	public enum Resolution {
		RESOLVED,
		DISMISSED
	}
}
