package com.pawmento.admin.application.port.out;

import java.util.Optional;

import com.pawmento.admin.application.port.in.ReportDetailResult;

public interface FindReportDetailPort {
	// 신고가 존재하면 대상 글이 삭제되었거나 스냅샷 열람이 제한돼도 신고 상세를 반환한다.
	Optional<ReportDetailResult> findDetail(long reportId);
}
