package com.pawmento.report.domain;

// 같은 회원이 같은 대상에 남긴 가장 최근 신고. 다시 신고할 수 있는지 판정할 때 쓴다
public record PreviousReport(Long reportId, boolean dismissed, Long targetVersion) {

	// 저장된 신고는 항상 대상 버전을 가진다. 없으면 데이터가 깨진 것이므로 판정하지 않는다
	public PreviousReport {
		if (targetVersion == null) {
			throw new IllegalStateException("이전 신고에 대상 버전이 없습니다.");
		}
	}

	// 기각된 뒤 대상이 고쳐졌을 때만 다시 접수한다. 처리 대기, 처리 완료는 거절한다.
	// 대상 버전은 줄지 않으므로, 기각된 신고보다 오래된 버전을 읽은 접수(읽은 뒤 기다린 요청)도 거절한다
	public boolean allowsNewReportOn(Long currentTargetVersion) {
		if (currentTargetVersion == null) {
			throw new IllegalArgumentException("현재 대상 버전이 필요합니다.");
		}
		return dismissed && currentTargetVersion > targetVersion;
	}
}
