package com.pawmento.community.application.port.in.report;

public interface ReportPostUseCase {

	// 접수한 신고의 id를 돌려준다
	Long report(ReportPostCommand command);

}
