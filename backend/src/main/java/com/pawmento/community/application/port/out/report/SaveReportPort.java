package com.pawmento.community.application.port.out.report;

import com.pawmento.community.domain.report.Report;

public interface SaveReportPort {

	// 처리 대기(PENDING) 신고로 저장하고 id를 돌려준다.
	// 같은 회원의 같은 글 PENDING 신고가 이미 있으면 DuplicateReportException이다
	Long save(Report report);

}
