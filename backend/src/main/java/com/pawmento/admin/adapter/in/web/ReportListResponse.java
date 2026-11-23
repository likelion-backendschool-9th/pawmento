package com.pawmento.admin.adapter.in.web;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.pawmento.admin.application.port.in.ListReportsUseCase.Page;

record ReportListResponse(
		List<ReportListItemResponse> items,
		@JsonInclude(JsonInclude.Include.ALWAYS) String nextCursor) {

	static ReportListResponse from(Page page) {
		var items = page.items().stream().map(ReportListItemResponse::from).toList();
		String nextCursor = page.nextCursor() == null ? null : page.nextCursor().toString();
		return new ReportListResponse(items, nextCursor);
	}
}
