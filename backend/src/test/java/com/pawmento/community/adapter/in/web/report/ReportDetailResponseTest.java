package com.pawmento.community.adapter.in.web.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.pawmento.community.adapter.in.web.report.ReportDetailResponse.Snapshot;
import com.pawmento.community.application.port.in.report.ListReportItemResult.PostState;
import com.pawmento.community.application.port.in.report.ListReportItemResult.ReasonType;
import com.pawmento.community.application.port.in.report.ReportStatus;

import tools.jackson.databind.json.JsonMapper;

class ReportDetailResponseTest {
	private final JsonMapper mapper = JsonMapper.builder().build();
	private final Instant now = Instant.parse("2026-11-25T00:00:00Z");

	@Test
	@DisplayName("#92-S2 삭제된 글의 상세 JSON은 접수 스냅샷과 현재 글 버전을 구분한다")
	void keepsSnapshotForDeletedPost() {
		var response = new ReportDetailResponse(101, 201, 301, 401, ReportStatus.PENDING, ReasonType.PRIVACY,
				"사유", now, 0, new Snapshot("당시 제목", "당시 본문", "DAILY", 2),
				false, PostState.DELETED, 3, null, null, null);
		var json = mapper.readTree(mapper.writeValueAsString(response));

		assertThat(json.properties().stream().map(Map.Entry::getKey).toList()).containsExactlyInAnyOrder(
				"reportId", "postId", "reporterId", "authorId", "status", "reasonType", "reasonDetail",
				"createdAt", "version", "snapshot", "snapshotExpired", "postState", "postVersion",
				"processedBy", "processedAt", "resolutionNote");
		assertThat(json.get("snapshot").get("postVersion").asLong()).isEqualTo(2);
		assertThat(json.get("postVersion").asLong()).isEqualTo(3);
		assertThat(json.get("postState").asString()).isEqualTo("DELETED");
		assertThat(json.get("createdAt").asString()).isEqualTo(now.toString());
	}

	@Test
	@DisplayName("#92-S2 만료 상세는 null 스냅샷과 만료 표시를 반환한다")
	void marksExpiredSnapshot() {
		var response = new ReportDetailResponse(101, 201, 301, 401, ReportStatus.DISMISSED, ReasonType.PRIVACY,
				null, now, 1, null, true, PostState.VISIBLE, 2, 501L, now, "기각");
		var json = mapper.readTree(mapper.writeValueAsString(response));

		assertThat(json.get("snapshot").isNull()).isTrue();
		assertThat(json.get("snapshotExpired").asBoolean()).isTrue();
		assertThat(json.get("reasonDetail").isNull()).isTrue();
		assertThat(json.get("processedBy").asLong()).isEqualTo(501);
		assertThat(json.get("processedAt").asString()).isEqualTo(now.toString());
		assertThat(json.get("resolutionNote").asString()).isEqualTo("기각");
	}
}
