package com.pawmento.community.adapter.in.web.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.pawmento.community.application.port.in.report.ListReportItemResult;
import com.pawmento.community.application.port.in.report.ListReportItemResult.PostState;
import com.pawmento.community.application.port.in.report.ListReportItemResult.ReasonType;
import com.pawmento.community.application.port.in.report.ReportStatus;
import com.pawmento.community.application.port.in.report.ListReportsUseCase.Page;

import tools.jackson.databind.json.JsonMapper;

class ReportListItemResponseTest {
	@Test
	@DisplayName("#92-S1 목록 JSON은 합의된 7개 필드만 제공하고 신고 당시 제목과 현재 상태를 구분한다 (항목 매핑 부분 검사)")
	void onlyAgreedSummaryFieldsAreExposed() {
		var mapper = JsonMapper.builder().build();
		var createdAt = Instant.parse("2026-11-21T00:00:00Z");
		var result = new ListReportItemResult(101L, 201L, ReportStatus.PENDING, ReasonType.PRIVACY, createdAt,
				"신고 당시 제목", PostState.DELETED);
		var page = mapper.readTree(mapper.writeValueAsString(ReportListResponse.from(new Page(List.of(result), 101L))));
		assertThat(page.properties().stream().map(Map.Entry::getKey).toList())
				.containsExactlyInAnyOrder("items", "nextCursor");
		assertThat(page.get("nextCursor").isString()).isTrue();
		assertThat(page.get("nextCursor").asString()).isEqualTo("101");
		var json = page.get("items").get(0);

		assertThat(json.properties().stream().map(Map.Entry::getKey).toList())
			.containsExactlyInAnyOrder("reportId", "postId", "status", "reasonType", "createdAt", "snapshotTitle", "postState");
		assertThat(json.get("reportId").asLong()).isEqualTo(101L);
		assertThat(json.get("postId").asLong()).isEqualTo(201L);
		assertThat(json.get("status").asString()).isEqualTo("PENDING");
		assertThat(json.get("reasonType").asString()).isEqualTo("PRIVACY");
		assertThat(json.get("createdAt").isString()).isTrue();
		assertThat(json.get("createdAt").asString()).isEqualTo("2026-11-21T00:00:00Z");
		assertThat(json.get("snapshotTitle").asString()).isEqualTo("신고 당시 제목");
		assertThat(json.get("postState").asString()).isEqualTo("DELETED");
	}

	@Test
	@DisplayName("#92-S1 빈 페이지는 빈 items와 명시적인 null 커서를 반환한다")
	void emptyPageIncludesNullCursor() {
		var mapper = JsonMapper.builder().build();
		var json = mapper.readTree(mapper.writeValueAsString(ReportListResponse.from(new Page(List.of(), null))));

		assertThat(json.get("items").isArray()).isTrue();
		assertThat(json.get("items").size()).isZero();
		assertThat(json.get("nextCursor").isNull()).isTrue();
	}
}
