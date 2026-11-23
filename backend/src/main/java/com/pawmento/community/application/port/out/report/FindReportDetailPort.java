package com.pawmento.community.application.port.out.report;

import java.util.Optional;

import com.pawmento.community.application.port.in.report.ReportDetailResult;

public interface FindReportDetailPort {
	// 신고가 존재하면 대상 글이 삭제되었거나 스냅샷 열람이 제한돼도 신고 상세를 반환한다.
	Optional<ReportDetailResult> findDetail(long reportId);
}
