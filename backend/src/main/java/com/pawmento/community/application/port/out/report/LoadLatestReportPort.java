package com.pawmento.community.application.port.out.report;

import java.util.Optional;

import com.pawmento.community.domain.report.PreviousReport;

public interface LoadLatestReportPort {

	// 같은 회원이 같은 글에 남긴 신고 중 가장 최근(id가 가장 큰) 것
	Optional<PreviousReport> findLatest(Long postId, Long reporterId);

}
