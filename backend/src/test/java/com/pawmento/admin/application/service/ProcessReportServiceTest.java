package com.pawmento.admin.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.pawmento.admin.application.exception.ReportNotFoundException;
import com.pawmento.admin.application.exception.ReportProcessingConflictException;
import com.pawmento.admin.application.port.in.ListReportItemResult.PostState;
import com.pawmento.admin.application.port.in.ListReportItemResult.ReasonType;
import com.pawmento.admin.application.port.in.ProcessReportCommand;
import com.pawmento.admin.application.port.in.ProcessReportCommand.Resolution;
import com.pawmento.admin.application.port.in.ReportDetailResult;
import com.pawmento.admin.application.port.in.ReportStatus;
import com.pawmento.admin.application.port.out.ApplyReportDecisionPort;
import com.pawmento.admin.application.port.out.ApplyReportDecisionPort.State;

class ProcessReportServiceTest {
	private static final Instant NOW = Instant.parse("2026-11-23T00:00:00Z");
	private static final long ADMIN_ID = 8;
	private final ApplyReportDecisionPort reports = mock(ApplyReportDecisionPort.class);
	private final ProcessReportService service = new ProcessReportService(reports, Clock.fixed(NOW, ZoneOffset.UTC));

	@ParameterizedTest
	@EnumSource(Resolution.class)
	@DisplayName("#92-S3 서버가 처리자와 처리 시각을 전달하고 조건부 저장 결과를 반환한다")
	void passesAuthenticatedActorAndServerTime(Resolution resolution) {
		var command = new ProcessReportCommand(101, resolution, 3, "확인 완료");
		var detail = new ReportDetailResult(101, 201, ReportStatus.valueOf(resolution.name()), ReasonType.PRIVACY,
				"신고 내용", NOW.minusSeconds(1), 4, null, new ReportDetailResult.Target(PostState.DELETED, null),
				new ReportDetailResult.Processing(ADMIN_ID, NOW, "확인 완료"));
		when(reports.findState(101)).thenReturn(Optional.of(new State(ReportStatus.PENDING, 3)));
		when(reports.apply(command, ADMIN_ID, NOW)).thenReturn(Optional.of(detail));

		assertThat(service.process(command, ADMIN_ID)).isEqualTo(detail);
	}

	@Test
	@DisplayName("#92-S5 없는 신고는 저장 요청 전에 미존재로 처리한다")
	void rejectsMissingReportBeforeWriting() {
		when(reports.findState(101)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.process(resolve(0), ADMIN_ID)).isInstanceOf(ReportNotFoundException.class);
		verify(reports).findState(101);
		verifyNoMoreInteractions(reports);
	}

	@Test
	@DisplayName("#92-S7 이미 처리된 신고는 버전이 같아도 다시 저장하지 않는다")
	void skipsWriteForAlreadyProcessedReport() {
		when(reports.findState(101)).thenReturn(Optional.of(new State(ReportStatus.RESOLVED, 4)));

		assertThatThrownBy(() -> service.process(resolve(4), ADMIN_ID))
				.isInstanceOf(ReportProcessingConflictException.class).hasMessageContaining("이미 처리");
		verify(reports).findState(101);
		verifyNoMoreInteractions(reports);
	}

	@Test
	@DisplayName("#92-S6 조회 뒤 경쟁 요청이 먼저 저장하면 조건부 저장 실패를 충돌로 처리한다")
	void reportsConflictWhenConditionalWriteLosesRace() {
		var command = resolve(3);
		when(reports.findState(101)).thenReturn(Optional.of(new State(ReportStatus.PENDING, 3)));
		when(reports.apply(command, ADMIN_ID, NOW)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.process(command, ADMIN_ID))
				.isInstanceOf(ReportProcessingConflictException.class);
		verify(reports, times(1)).apply(command, ADMIN_ID, NOW);
	}

	private static ProcessReportCommand resolve(long version) {
		return new ProcessReportCommand(101, Resolution.RESOLVED, version, null);
	}
}
