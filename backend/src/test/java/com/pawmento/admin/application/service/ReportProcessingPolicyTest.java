package com.pawmento.admin.application.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.pawmento.admin.application.exception.ReportProcessingConflictException;
import com.pawmento.admin.application.port.in.ReportStatus;

class ReportProcessingPolicyTest {
	@Test
	@DisplayName("#92-S3 미처리 상태에 현재 버전이 일치하면 처리할 수 있다")
	void allowsMatchingPendingReport() {
		assertThatCode(() -> ReportProcessingPolicy.requireProcessable(ReportStatus.PENDING, 0, 0))
				.doesNotThrowAnyException();
	}

	@Test
	@DisplayName("#92-S6 미처리 신고도 이전 버전의 요청은 충돌로 거절한다")
	void rejectsStaleVersion() {
		assertThatThrownBy(() -> ReportProcessingPolicy.requireProcessable(ReportStatus.PENDING, 2, 1))
				.isInstanceOf(ReportProcessingConflictException.class)
				.hasMessageContaining("변경");
	}

	@ParameterizedTest
	@EnumSource(value = ReportStatus.class, names = {"RESOLVED", "DISMISSED"})
	@DisplayName("#92-S7 처리 완료나 기각된 신고는 버전이 같아도 재처리할 수 없다")
	void rejectsRepeatedProcessing(ReportStatus status) {
		assertThatThrownBy(() -> ReportProcessingPolicy.requireProcessable(status, 1, 1))
				.isInstanceOf(ReportProcessingConflictException.class)
				.hasMessageContaining("이미 처리");
	}
}
