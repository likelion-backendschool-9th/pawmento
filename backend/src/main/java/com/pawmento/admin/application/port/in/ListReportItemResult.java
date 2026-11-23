package com.pawmento.admin.application.port.in;

import java.time.Instant;

// 관리자 목록에 허용된 정보만 담는다. 페이지·커서와 상세 정보는 별도 계약이다.
public record ListReportItemResult(
		Long reportId,
		Long postId,
		ListReportsQuery.Status status,
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
