package com.pawmento.admin.application.port.in;

public interface ProcessReportUseCase {
	ReportDetailResult process(ProcessReportCommand command, long authenticatedAdminId);
}
