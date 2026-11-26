package com.pawmento.report.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PreviousReportTest {

	@Test
	@DisplayName("#112-S4 기각된 뒤 대상 버전이 기각된 신고보다 클 때만 다시 신고할 수 있다")
	void allowsOnlyDismissedWithNewerVersion() {
		PreviousReport dismissedAt3 = new PreviousReport(1L, true, 3L);
		// 기각된 신고보다 오래된 버전은 읽은 뒤 기다린 접수다
		assertThat(dismissedAt3.allowsNewReportOn(2L)).isFalse();
		assertThat(dismissedAt3.allowsNewReportOn(3L)).isFalse();
		assertThat(dismissedAt3.allowsNewReportOn(4L)).isTrue();
		// 처리 대기와 처리 완료는 기각이 아니라 버전과 상관없이 거절한다
		assertThat(new PreviousReport(1L, false, 2L).allowsNewReportOn(3L)).isFalse();
	}
}
