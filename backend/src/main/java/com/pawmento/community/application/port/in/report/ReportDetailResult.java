package com.pawmento.community.application.port.in.report;

import java.time.Instant;

import com.pawmento.community.application.port.in.report.ListReportItemResult.PostState;
import com.pawmento.community.application.port.in.report.ListReportItemResult.ReasonType;

public record ReportDetailResult(long reportId, long postId, ReportStatus status, ReasonType reasonType,
		String reasonDetail, Instant createdAt, long version, Snapshot snapshot, Target target, Processing processing) {

	public record Snapshot(String title, String body, String categoryCode, Instant capturedAt, long postVersion) {
	}

	public record Target(PostState state, Long postVersion) {
	}

	public record Processing(long processedBy, Instant processedAt, String memo) {
	}
}
