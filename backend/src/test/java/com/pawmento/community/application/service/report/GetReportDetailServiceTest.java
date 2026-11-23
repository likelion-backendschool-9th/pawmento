package com.pawmento.community.application.service.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.pawmento.community.application.exception.report.ReportNotFoundException;
import com.pawmento.community.application.port.in.report.GetReportDetailQuery;
import com.pawmento.community.application.port.in.report.ListReportItemResult.PostState;
import com.pawmento.community.application.port.out.report.FindReportDetailPort.Detail;
import com.pawmento.community.application.port.out.report.FindReportDetailPort.Snapshot;
import com.pawmento.community.application.port.out.report.FindReportDetailPort.Target;

class GetReportDetailServiceTest {
	@Test
	@DisplayName("#92-S2 대상 글이 삭제돼도 존재하는 신고와 당시 스냅샷을 반환한다")
	void keepsExistingReportWhenTargetWasDeleted() {
		var snapshot = new Snapshot("신고 시 제목", "신고 시 본문", "FREE", Instant.EPOCH, 2);
		var report = new Detail(101, 201, "PENDING", "PRIVACY", "신고 이유",
				Instant.EPOCH, 0, snapshot, new Target("DELETED", null), null);
		var service = new GetReportDetailService(id -> id == 101 ? Optional.of(report) : Optional.empty());

		var result = service.get(new GetReportDetailQuery(8, 101));
		assertThat(result.reportId()).isEqualTo(101L);
		assertThat(result.target().state()).isEqualTo(PostState.DELETED);
		assertThat(result.snapshot().body()).isEqualTo("신고 시 본문");
	}

	@Test
	@DisplayName("#92-S5 대상 글 상태와 별개로 신고 자체가 없으면 미존재로 처리한다")
	void reportsMissingReport() {
		var service = new GetReportDetailService(id -> Optional.empty());

		assertThatThrownBy(() -> service.get(new GetReportDetailQuery(8, 999))).isInstanceOf(ReportNotFoundException.class);
	}
}
