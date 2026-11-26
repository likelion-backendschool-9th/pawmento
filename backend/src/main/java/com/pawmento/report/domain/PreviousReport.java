package com.pawmento.report.domain;

// 같은 회원이 같은 대상에 남긴 가장 최근 신고. 다시 신고할 수 있는지 판정할 때 쓴다
public record PreviousReport(Long reportId, boolean dismissed, Long targetVersion) {

	// 기각된 뒤 대상이 고쳐졌을 때만 다시 접수한다. 처리 대기, 처리 완료는 거절한다.
	// 대상 버전은 줄지 않으므로, 기각된 신고보다 오래된 버전을 읽은 접수(읽은 뒤 기다린 요청)도 거절한다
	public boolean allowsNewReportOn(Long currentTargetVersion) {
		return dismissed && currentTargetVersion > targetVersion;
	}
}
