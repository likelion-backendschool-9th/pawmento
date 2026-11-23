package com.pawmento.admin.application.service;

import com.pawmento.admin.application.exception.ReportProcessingConflictException;
import com.pawmento.admin.application.port.in.ReportStatus;

public final class ReportProcessingPolicy {
	private ReportProcessingPolicy() {
	}

	// 저장 시에도 같은 상태와 버전에 대한 조건부 쓰기가 필요하다.
	public static void requireProcessable(ReportStatus status, long currentVersion, long expectedVersion) {
		if (status != ReportStatus.PENDING) {
			throw new ReportProcessingConflictException("이미 처리된 신고예요. 최신 신고 정보를 확인해 주세요.");
		}
		if (currentVersion != expectedVersion) {
			throw new ReportProcessingConflictException("신고 정보가 변경됐어요. 최신 신고 정보를 확인해 주세요.");
		}
	}
}
