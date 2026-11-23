package com.pawmento.community.adapter.in.web.report;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.pawmento.community.application.port.in.report.ListReportsUseCase.Page;

record ReportListResponse(
		List<ReportListItemResponse> items,
		@JsonInclude(JsonInclude.Include.ALWAYS) String nextCursor) {

	static ReportListResponse from(Page page) {
		var items = page.items().stream().map(ReportListItemResponse::from).toList();
		String nextCursor = page.nextCursor() == null ? null : page.nextCursor().toString();
		return new ReportListResponse(items, nextCursor);
	}
}
