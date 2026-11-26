package com.pawmento.report.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PreviousReportTest {

	@Test
	@DisplayName("#112-S4 기각된 뒤 대상 버전이 달라졌을 때만 다시 신고할 수 있다")
	void allowsOnlyDismissedWithChangedVersion() {
		assertThat(new PreviousReport(1L, true, 2L).allowsNewReportOn(3L)).isTrue();
		assertThat(new PreviousReport(1L, true, 2L).allowsNewReportOn(2L)).isFalse();
		// 처리 대기와 처리 완료는 기각이 아니라 버전과 상관없이 거절한다
		assertThat(new PreviousReport(1L, false, 2L).allowsNewReportOn(3L)).isFalse();
	}
}
