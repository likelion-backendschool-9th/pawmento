package com.pawmento.community.application.service.report;

import com.pawmento.community.application.port.in.report.ListReportItemResult;
import com.pawmento.community.application.port.in.report.ListReportItemResult.PostState;
import com.pawmento.community.application.port.in.report.ListReportItemResult.ReasonType;
import com.pawmento.community.application.port.in.report.ListReportsQuery;
import com.pawmento.community.application.port.in.report.ListReportsUseCase;
import com.pawmento.community.application.port.in.report.ReportStatus;
import com.pawmento.community.application.port.out.report.FindReportPagePort;
import com.pawmento.community.application.port.out.report.FindReportPagePort.Criteria;
import com.pawmento.community.application.port.out.report.FindReportPagePort.Item;

public class ListReportsService implements ListReportsUseCase {
	private final FindReportPagePort reports;

	public ListReportsService(FindReportPagePort reports) {
		this.reports = reports;
	}

	@Override
	public Page list(ListReportsQuery query) {
		var match = reports.find(new Criteria(query.status().name(), query.size(), query.beforeReportId()));
		var items = match.items();
		Long nextCursor = match.hasMore() ? items.getLast().reportId() : null;
		return new Page(items.stream().map(ListReportsService::toResult).toList(), nextCursor);
	}

	private static ListReportItemResult toResult(Item item) {
		return new ListReportItemResult(item.reportId(), item.postId(), ReportStatus.valueOf(item.status()),
				ReasonType.valueOf(item.reasonType()), item.createdAt(), item.snapshotTitle(),
				PostState.valueOf(item.postState()));
	}
}
