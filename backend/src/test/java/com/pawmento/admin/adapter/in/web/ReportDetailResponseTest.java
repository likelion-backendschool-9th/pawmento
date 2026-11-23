package com.pawmento.admin.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.pawmento.admin.application.port.in.ListReportItemResult.PostState;
import com.pawmento.admin.application.port.in.ListReportItemResult.ReasonType;
import com.pawmento.admin.application.port.in.ReportDetailResult;
import com.pawmento.admin.application.port.in.ReportStatus;

import tools.jackson.databind.json.JsonMapper;

class ReportDetailResponseTest {
	private final JsonMapper mapper = JsonMapper.builder().build();

	@Test
	@DisplayName("#92-S2 신고 당시 스냅샷과 삭제된 게시글 상태를 별도 필드로 반환한다")
	void separatesSnapshotAndCurrentTarget() {
		var snapshot = new ReportDetailResult.Snapshot("신고 시 제목", "신고 시 본문", "FREE", Instant.EPOCH, 2);
		var processing = new ReportDetailResult.Processing(8, Instant.EPOCH.plusSeconds(1), "확인 완료");
		var report = new ReportDetailResult(101, 201, ReportStatus.DISMISSED, ReasonType.PRIVACY, "신고 이유",
				Instant.EPOCH, 1, snapshot, new ReportDetailResult.Target(PostState.DELETED, null), processing);
		var json = mapper.valueToTree(ReportDetailResponse.from(report));

		assertThat(json.properties().stream().map(Map.Entry::getKey).toList()).containsExactlyInAnyOrder(
				"reportId", "postId", "status", "reasonType", "reasonDetail", "createdAt", "version",
				"snapshot", "target", "processing");
		assertThat(json.get("snapshot").get("body").asString()).isEqualTo("신고 시 본문");
		assertThat(json.get("snapshot").get("capturedAt").asString()).isEqualTo("1970-01-01T00:00:00Z");
		assertThat(json.get("target").properties().stream().map(Map.Entry::getKey).toList())
				.containsExactlyInAnyOrder("state", "postVersion");
		assertThat(json.get("target").get("state").asString()).isEqualTo("DELETED");
		assertThat(json.get("processing").get("processedBy").asLong()).isEqualTo(8L);
		assertThat(json.get("processing").get("processedAt").asString()).isEqualTo("1970-01-01T00:00:01Z");
	}

	@Test
	@DisplayName("#92-S2 열람 가능한 스냅샷이 없으면 현재 글 상태만 전달한다")
	void keepsUnavailableSnapshotOutOfTheResponse() {
		var report = new ReportDetailResult(101, 201, ReportStatus.PENDING, ReasonType.PRIVACY, "신고 이유",
				Instant.EPOCH, 0, null, new ReportDetailResult.Target(PostState.VISIBLE, 3L), null);
		var json = mapper.valueToTree(ReportDetailResponse.from(report));

		assertThat(json.get("snapshot").isNull()).isTrue();
		assertThat(json.get("target").get("state").asString()).isEqualTo("VISIBLE");
		assertThat(json.get("processing").isNull()).isTrue();
	}
}
