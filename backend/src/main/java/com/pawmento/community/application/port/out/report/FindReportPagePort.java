package com.pawmento.community.application.port.out.report;

import java.time.Instant;
import java.util.List;

public interface FindReportPagePort {
	/**
	 * Returns at most criteria.size() reports of the requested status, in descending ID order.
	 * When present, beforeReportId is an exclusive upper bound.
	 * hasMore indicates that another report matches beyond the last returned item.
	 */
	Match find(Criteria criteria);

	record Criteria(String status, int size, Long beforeReportId) {
	}

	record Match(List<Item> items, boolean hasMore) {
	}

	record Item(Long reportId, Long postId, String status, String reasonType, Instant createdAt,
			String snapshotTitle, String postState) {
	}
}
