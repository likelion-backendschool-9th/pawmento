package com.pawmento.community.application.port.out.report;

import java.util.List;

import com.pawmento.community.application.port.in.report.ListReportItemResult;
import com.pawmento.community.application.port.in.report.ListReportsQuery;

public interface FindReportPagePort {
	/**
	 * Returns at most query.size() reports of the requested status, in descending ID order.
	 * When present, beforeReportId is an exclusive upper bound.
	 * hasMore indicates that another report matches beyond the last returned item.
	 */
	Match find(ListReportsQuery query);

	record Match(List<ListReportItemResult> items, boolean hasMore) {
	}
}
