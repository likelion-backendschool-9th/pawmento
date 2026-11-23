package com.pawmento.community.application.port.in.report;

public interface ProcessReportUseCase {
	ReportDetailResult process(ProcessReportCommand command, long authenticatedAdminId);
}
