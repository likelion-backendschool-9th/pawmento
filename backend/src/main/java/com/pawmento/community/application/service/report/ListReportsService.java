package com.pawmento.community.application.service.report;

import com.pawmento.community.application.port.in.report.ListReportsQuery;
import com.pawmento.community.application.port.in.report.ListReportsUseCase;
import com.pawmento.community.application.port.out.report.FindReportPagePort;

public class ListReportsService implements ListReportsUseCase {
	private final FindReportPagePort reports;

	public ListReportsService(FindReportPagePort reports) {
		this.reports = reports;
	}

	@Override
	public Page list(ListReportsQuery query) {
		var match = reports.find(query);
		var items = match.items();
		Long nextCursor = match.hasMore() ? items.getLast().reportId() : null;
		return new Page(items, nextCursor);
	}
}
