package com.pawmento.admin.application.port.out;

import java.time.Instant;
import java.util.Optional;

import com.pawmento.admin.application.port.in.ProcessReportCommand;
import com.pawmento.admin.application.port.in.ReportDetailResult;
import com.pawmento.admin.application.port.in.ReportStatus;

public interface ApplyReportDecisionPort {
	Optional<State> findState(long reportId);

	/**
	 * PENDING이고 기대 버전이 일치할 때만 처리 정보와 새 버전을 기록한다.
	 * 게시글 조치가 필요한 결정이면 같은 원자적 작업에서 적용한다.
	 * 조건 불일치로 적용하지 못하면 비어 있는 결과를 반환한다.
	 */
	Optional<ReportDetailResult> apply(ProcessReportCommand command, long administratorId, Instant processedAt);

	record State(ReportStatus status, long version) {
	}
}
