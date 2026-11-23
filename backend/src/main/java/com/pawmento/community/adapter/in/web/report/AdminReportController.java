package com.pawmento.community.adapter.in.web.report;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.pawmento.community.adapter.in.web.report.exception.InvalidReportIdException;
import com.pawmento.community.application.port.in.report.GetReportDetailQuery;
import com.pawmento.community.application.port.in.report.GetReportDetailUseCase;
import com.pawmento.community.application.port.in.report.ListReportsUseCase;
import com.pawmento.community.application.port.in.report.ProcessReportUseCase;
import com.pawmento.global.security.LoginMember;

// ADMIN 경로 보호와 실제 조회·처리 어댑터가 준비되면 컨트롤러를 등록한다.
@RequestMapping("/admin/reports")
abstract class AdminReportController {
	private final ListReportsUseCase listReports;
	private final GetReportDetailUseCase getReportDetail;
	private final ProcessReportUseCase processReport;

	AdminReportController(ListReportsUseCase listReports, GetReportDetailUseCase getReportDetail,
			ProcessReportUseCase processReport) {
		this.listReports = listReports;
		this.getReportDetail = getReportDetail;
		this.processReport = processReport;
	}

	@GetMapping
	ReportListResponse list(@RequestParam(name = "status", required = false) String status,
			@RequestParam(name = "size", required = false) String size,
			@RequestParam(name = "cursor", required = false) String cursor) {
		var query = new ListReportsRequest(status, size, cursor).toQuery();
		return ReportListResponse.from(listReports.list(query));
	}

	@GetMapping("/{reportId}")
	ReportDetailResponse detail(@PathVariable("reportId") long reportId, @LoginMember Long administratorId) {
		if (reportId <= 0) {
			throw new InvalidReportIdException();
		}
		return ReportDetailResponse.from(getReportDetail.get(new GetReportDetailQuery(administratorId, reportId)));
	}

	@PatchMapping("/{reportId}")
	ReportDetailResponse process(@PathVariable("reportId") long reportId, @LoginMember Long administratorId,
			@RequestBody ProcessReportRequest request) {
		return ReportDetailResponse.from(processReport.process(request.toCommand(reportId, administratorId)));
	}
}
