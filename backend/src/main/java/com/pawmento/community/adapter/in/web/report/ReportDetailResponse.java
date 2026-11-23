package com.pawmento.community.adapter.in.web.report;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.pawmento.community.application.port.in.report.ReportDetailResult;

record ReportDetailResponse(long reportId, long postId, String status, String reasonType, String reasonDetail,
		@JsonFormat(shape = JsonFormat.Shape.STRING) Instant createdAt, long version,
		Snapshot snapshot, Target target, Processing processing) {

	static ReportDetailResponse from(ReportDetailResult result) {
		var snapshot = result.snapshot();
		var target = result.target();
		var processing = result.processing();
		return new ReportDetailResponse(result.reportId(), result.postId(), result.status().name(),
				result.reasonType().name(), result.reasonDetail(), result.createdAt(), result.version(),
				snapshot == null ? null : new Snapshot(snapshot.title(), snapshot.body(), snapshot.categoryCode(),
						snapshot.capturedAt(), snapshot.postVersion()),
				new Target(target.state().name(), target.postVersion()),
				processing == null ? null : new Processing(processing.processedBy(), processing.processedAt(),
						processing.memo()));
	}

	record Snapshot(String title, String body, String categoryCode,
			@JsonFormat(shape = JsonFormat.Shape.STRING) Instant capturedAt, long postVersion) {
	}

	record Target(String state, Long postVersion) {
	}

	record Processing(long processedBy, @JsonFormat(shape = JsonFormat.Shape.STRING) Instant processedAt, String memo) {
	}
}
