package com.pawmento.community.application.port.in.report;

import java.util.List;

public interface ListReportsUseCase {
	Page list(ListReportsQuery query);

	record Page(List<ListReportItemResult> items, Long nextCursor) {
		public Page {
			items = List.copyOf(items);
		}
	}
}
