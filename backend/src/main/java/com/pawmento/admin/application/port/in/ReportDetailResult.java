package com.pawmento.admin.application.port.in;

import java.time.Instant;

import com.pawmento.admin.application.port.in.ListReportItemResult.PostState;
import com.pawmento.admin.application.port.in.ListReportItemResult.ReasonType;

public record ReportDetailResult(long reportId, long postId, ReportStatus status, ReasonType reasonType,
		String reasonDetail, Instant createdAt, long version, Snapshot snapshot, Target target, Processing processing) {

	public record Snapshot(String title, String body, String categoryCode, Instant capturedAt, long postVersion) {
	}

	public record Target(PostState state, Long postVersion) {
	}

	public record Processing(long processedBy, Instant processedAt, String memo) {
	}
}
