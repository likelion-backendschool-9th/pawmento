package com.pawmento.community.application.service.report;

import com.pawmento.community.application.exception.report.ReportNotFoundException;
import com.pawmento.community.application.port.in.report.GetReportDetailUseCase;
import com.pawmento.community.application.port.in.report.ReportDetailResult;
import com.pawmento.community.application.port.out.report.FindReportDetailPort;

public class GetReportDetailService implements GetReportDetailUseCase {
	private final FindReportDetailPort reports;

	public GetReportDetailService(FindReportDetailPort reports) {
		this.reports = reports;
	}

	@Override
	public ReportDetailResult get(long reportId) {
		return reports.findDetail(reportId).orElseThrow(ReportNotFoundException::new);
	}
}
