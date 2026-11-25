package com.pawmento.community.adapter.in.web.report;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.pawmento.community.application.port.in.report.ListReportItemResult.PostState;
import com.pawmento.community.application.port.in.report.ListReportItemResult.ReasonType;
import com.pawmento.community.application.port.in.report.ReportStatus;

record ReportDetailResponse(
		long reportId,
		long postId,
		long reporterId,
		long authorId,
		ReportStatus status,
		ReasonType reasonType,
		String reasonDetail,
		@JsonFormat(shape = JsonFormat.Shape.STRING) Instant createdAt,
		long version,
		Snapshot snapshot,
		boolean snapshotExpired,
		PostState postState,
		long postVersion,
		Long processedBy,
		@JsonFormat(shape = JsonFormat.Shape.STRING) Instant processedAt,
		String resolutionNote) {

	record Snapshot(String title, String body, String categoryCode, long postVersion) {
	}
}
