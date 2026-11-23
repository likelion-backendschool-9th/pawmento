package com.pawmento.admin.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.pawmento.admin.adapter.in.web.exception.InvalidReportListRequestException;
import com.pawmento.admin.application.port.in.ListReportsQuery;
import com.pawmento.admin.application.port.in.ListReportsQuery.Status;

class ListReportsRequestTest {
	@Test
	@DisplayName("#92-S1 조회 조건을 생략하면 PENDING 첫 페이지를 20건씩 조회한다")
	void defaultsToFirstPendingPage() {
		assertThat(new ListReportsRequest(null, null, null).toQuery())
				.isEqualTo(new ListReportsQuery(Status.PENDING, 20, null));
	}

	@Test
	@DisplayName("#92-S1 명시한 상태와 페이지 크기 경계, Long 범위의 신고 ID 커서를 유지한다")
	void acceptsSupportedPageBoundaries() {
		assertThat(new ListReportsRequest("RESOLVED", "1", "2").toQuery())
				.isEqualTo(new ListReportsQuery(Status.RESOLVED, 1, 2L));
		assertThat(new ListReportsRequest("DISMISSED", "50", Long.toString(Long.MAX_VALUE)).toQuery())
				.isEqualTo(new ListReportsQuery(Status.DISMISSED, 50, Long.MAX_VALUE));
	}

	@ParameterizedTest
	@ValueSource(strings = {"", "pending", "OPEN"})
	@DisplayName("#92-S5 빈 값과 허용되지 않은 신고 상태는 목록 입력 오류다")
	void rejectsInvalidStatus(String status) {
		assertThatThrownBy(() -> new ListReportsRequest(status, null, null).toQuery())
				.isInstanceOf(InvalidReportListRequestException.class)
				.hasMessage("status는 PENDING, RESOLVED, DISMISSED 중 하나여야 해요.");
	}

	@ParameterizedTest
	@ValueSource(strings = {"", "0", "-1", "51", "2.0", "a", "9223372036854775808"})
	@DisplayName("#92-S5 형식이 잘못되거나 1~50 범위 밖인 크기는 목록 입력 오류다")
	void rejectsInvalidSize(String size) {
		assertThatThrownBy(() -> new ListReportsRequest(null, size, null).toQuery())
				.isInstanceOf(InvalidReportListRequestException.class)
				.hasMessage("size는 1 이상 50 이하의 정수여야 해요.");
	}

	@ParameterizedTest
	@ValueSource(strings = {"", "0", "-1", " 2", "+2", "1e3", "9223372036854775808"})
	@DisplayName("#92-S5 양의 10진수 Long이 아닌 커서는 목록 입력 오류다")
	void rejectsInvalidCursor(String cursor) {
		assertThatThrownBy(() -> new ListReportsRequest(null, null, cursor).toQuery())
				.isInstanceOf(InvalidReportListRequestException.class)
				.hasMessage("cursor는 양의 정수 신고 ID여야 해요.");
	}
}
