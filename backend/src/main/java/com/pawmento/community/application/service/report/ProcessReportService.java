package com.pawmento.community.application.service.report;

import java.time.Clock;

import com.pawmento.community.application.exception.report.ReportNotFoundException;
import com.pawmento.community.application.exception.report.ReportProcessingConflictException;
import com.pawmento.community.application.port.in.report.ProcessReportCommand;
import com.pawmento.community.application.port.in.report.ProcessReportUseCase;
import com.pawmento.community.application.port.in.report.ReportDetailResult;
import com.pawmento.community.application.port.out.report.ApplyReportDecisionPort;

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
