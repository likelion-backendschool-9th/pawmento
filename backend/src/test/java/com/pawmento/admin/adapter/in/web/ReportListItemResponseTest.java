package com.pawmento.admin.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.pawmento.admin.application.port.in.ListReportItemResult;

import tools.jackson.databind.json.JsonMapper;

class ReportListItemResponseTest {
	@Test
	@DisplayName("#92-S1 목록 JSON은 합의된 7개 필드만 제공하고 신고 당시 제목과 현재 상태를 구분한다 (항목 매핑 부분 검사)")
	void onlyAgreedSummaryFieldsAreExposed() {
		var mapper = JsonMapper.builder().build();
		var createdAt = Instant.parse("2026-11-21T00:00:00Z");
		var result = new ListReportItemResult(101L, 201L, "PENDING", "PRIVACY", createdAt, "신고 당시 제목", "DELETED");
		var json = mapper.readTree(mapper.writeValueAsString(ReportListItemResponse.from(result)));

		assertThat(json.properties().stream().map(Map.Entry::getKey).toList())
			.containsExactlyInAnyOrder("reportId", "postId", "status", "reasonType", "createdAt", "snapshotTitle", "postState");
		assertThat(json.get("reportId").asLong()).isEqualTo(101L);
		assertThat(json.get("postId").asLong()).isEqualTo(201L);
		assertThat(json.get("status").asString()).isEqualTo("PENDING");
		assertThat(json.get("reasonType").asString()).isEqualTo("PRIVACY");
		assertThat(json.get("createdAt")).isEqualTo(mapper.valueToTree(createdAt));
		assertThat(json.get("snapshotTitle").asString()).isEqualTo("신고 당시 제목");
		assertThat(json.get("postState").asString()).isEqualTo("DELETED");
	}
}
