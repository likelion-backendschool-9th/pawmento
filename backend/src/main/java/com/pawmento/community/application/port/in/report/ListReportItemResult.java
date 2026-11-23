package com.pawmento.community.application.port.in.report;

import java.time.Instant;

// 관리자 목록에 허용된 정보만 담는다. 페이지·커서와 상세 정보는 별도 계약이다.
public record ListReportItemResult(
		Long reportId,
		Long postId,
		ReportStatus status,
		ReasonType reasonType,
		Instant createdAt,
		String snapshotTitle,
		PostState postState) {
	public enum ReasonType {
		SPAM, ABUSE, HARMFUL, MISINFO, PRIVACY, OTHER
	}

	public enum PostState {
		VISIBLE, HIDDEN, DELETED
	}
}
