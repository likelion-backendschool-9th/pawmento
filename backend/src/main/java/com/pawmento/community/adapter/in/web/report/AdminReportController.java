package com.pawmento.community.adapter.in.web.report;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.pawmento.community.application.port.in.report.ListReportsUseCase;

import jakarta.validation.Valid;

// 실제 조회 어댑터가 준비되면 컨트롤러를 등록한다.
@RequestMapping("/admin/reports")
abstract class AdminReportController {
	private final ListReportsUseCase listReports;
	AdminReportController(ListReportsUseCase listReports) {
		this.listReports = listReports;
	}

	@GetMapping
	ReportListResponse list(@Valid ListReportsRequest request) {
		return ReportListResponse.from(listReports.list(request.toQuery()));
	}

}
