package com.pawmento.report.domain;

import com.pawmento.report.domain.exception.InvalidReportException;

// 접수 트랜잭션에서 대상 도메인이 한 번 읽어 준 신고 대상. 대상 도메인의 객체 대신 신고에 필요한 값만 담는다.
// categoryCode는 글에만 있고 다른 대상은 null이다
public record ReportTarget(
	TargetType type,
	Long id,
	Long authorId,
	Long version,
	String title,
	String body,
	String categoryCode
) {

	public ReportTarget {
		if (type == null || id == null || id <= 0 || authorId == null || version == null) {
			throw new InvalidReportException("신고할 대상이 필요합니다.");
		}
	}

	public boolean writtenBy(Long memberId) {
		return authorId.equals(memberId);
	}
}
