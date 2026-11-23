package com.pawmento.community.adapter.in.web.report;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.pawmento.community.application.port.in.report.ListReportsUseCase;

import jakarta.servlet.http.HttpServletRequest;

// 실제 조회 어댑터가 준비되면 컨트롤러를 등록한다.
@RequestMapping("/admin/reports")
abstract class AdminReportController {
	private final ListReportsUseCase listReports;
	AdminReportController(ListReportsUseCase listReports) {
		this.listReports = listReports;
	}

	@GetMapping
	ReportListResponse list(HttpServletRequest request) {
		var criteria = ReportListQueryParameters.parse(request.getQueryString());
		return ReportListResponse.from(listReports.list(criteria.toQuery()));
	}

}
