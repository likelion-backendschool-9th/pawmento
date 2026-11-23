package com.pawmento.community.adapter.in.web.report;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.pawmento.community.application.port.in.report.ListReportsUseCase;

// 실제 조회 어댑터가 준비되면 컨트롤러를 등록한다.
@RequestMapping("/admin/reports")
abstract class AdminReportController {
	private final ListReportsUseCase listReports;
	AdminReportController(ListReportsUseCase listReports) {
		this.listReports = listReports;
	}

	@GetMapping
	ReportListResponse list(@RequestParam(name = "status", required = false) String status,
			@RequestParam(name = "size", required = false) String size,
			@RequestParam(name = "cursor", required = false) String cursor) {
		var query = new ListReportsRequest(status, size, cursor).toQuery();
		return ReportListResponse.from(listReports.list(query));
	}

}
