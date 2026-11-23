package com.pawmento.admin.application.service;

import com.pawmento.admin.application.exception.ReportNotFoundException;
import com.pawmento.admin.application.port.in.GetReportDetailUseCase;
import com.pawmento.admin.application.port.in.ReportDetailResult;
import com.pawmento.admin.application.port.out.FindReportDetailPort;

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
