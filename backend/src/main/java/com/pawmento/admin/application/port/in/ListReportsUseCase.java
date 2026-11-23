package com.pawmento.admin.application.port.in;

import java.util.List;

public interface ListReportsUseCase {
	Page list(ListReportsQuery query);

	record Page(List<ListReportItemResult> items, Long nextCursor) {
		public Page {
			items = List.copyOf(items);
		}
	}
}
