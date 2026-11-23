package com.pawmento.admin.application.service;

import com.pawmento.admin.application.port.in.ListReportsQuery;
import com.pawmento.admin.application.port.in.ListReportsUseCase;
import com.pawmento.admin.application.port.out.FindReportPagePort;

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
