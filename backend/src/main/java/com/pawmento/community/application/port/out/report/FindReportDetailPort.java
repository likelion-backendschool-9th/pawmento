package com.pawmento.community.application.port.out.report;

import java.time.Instant;
import java.util.Optional;

public interface FindReportDetailPort {
	// 신고가 존재하면 대상 글이 삭제되었거나 스냅샷 열람이 제한돼도 신고 상세를 반환한다.
	Optional<Detail> findDetail(long reportId);

	record Detail(long reportId, long postId, String status, String reasonType, String reasonDetail,
			Instant createdAt, long version, Snapshot snapshot, Target target, Processing processing) {
	}

	record Snapshot(String title, String body, String categoryCode, Instant capturedAt, long postVersion) {
	}

	record Target(String state, Long postVersion) {
	}

	record Processing(long processedBy, Instant processedAt, String memo) {
	}
}
