package com.pawmento.admin.adapter.in.web;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.pawmento.admin.application.port.in.ListReportItemResult;

// HTTP 경로를 추가하지 않는다. 실제 조회 연결 시 사용할 목록 항목 매핑이다.
public record ReportListItemResponse(
		Long reportId,
		Long postId,
		String status,
		String reasonType,
		@JsonFormat(shape = JsonFormat.Shape.STRING) Instant createdAt,
		String snapshotTitle,
		String postState) {

	static ReportListItemResponse from(ListReportItemResult result) {
		return new ReportListItemResponse(result.reportId(), result.postId(), result.status().name(),
				result.reasonType().name(), result.createdAt(), result.snapshotTitle(), result.postState().name());
	}
}
