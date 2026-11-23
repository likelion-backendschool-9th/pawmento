package com.pawmento.admin.application.service;

import java.time.Clock;

import com.pawmento.admin.application.exception.ReportNotFoundException;
import com.pawmento.admin.application.exception.ReportProcessingConflictException;
import com.pawmento.admin.application.port.in.ProcessReportCommand;
import com.pawmento.admin.application.port.in.ProcessReportUseCase;
import com.pawmento.admin.application.port.in.ReportDetailResult;
import com.pawmento.admin.application.port.out.ApplyReportDecisionPort;

public class ProcessReportService implements ProcessReportUseCase {
	private final ApplyReportDecisionPort reports;
	private final Clock clock;

	public ProcessReportService(ApplyReportDecisionPort reports, Clock clock) {
		this.reports = reports;
		this.clock = clock;
	}

	@Override
	public ReportDetailResult process(ProcessReportCommand command, long authenticatedAdminId) {
		var state = reports.findState(command.reportId()).orElseThrow(ReportNotFoundException::new);
		ReportProcessingPolicy.requireProcessable(state.status(), state.version(), command.expectedVersion());
		return reports.apply(command, authenticatedAdminId, clock.instant())
				.orElseThrow(() -> new ReportProcessingConflictException(
						"신고 정보가 변경됐어요. 최신 신고 정보를 확인해 주세요."));
	}
}
