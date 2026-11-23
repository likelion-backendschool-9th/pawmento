package com.pawmento.admin.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.stream.LongStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.pawmento.admin.application.port.in.ListReportItemResult;
import com.pawmento.admin.application.port.in.ListReportItemResult.PostState;
import com.pawmento.admin.application.port.in.ListReportItemResult.ReasonType;
import com.pawmento.admin.application.port.in.ListReportsQuery;
import com.pawmento.admin.application.port.in.ListReportsQuery.Status;
import com.pawmento.admin.application.port.out.FindReportPagePort;
import com.pawmento.admin.application.port.out.FindReportPagePort.Match;

class ListReportsServiceTest {
	private final FindReportPagePort reports = mock(FindReportPagePort.class);
	private final ListReportsService service = new ListReportsService(reports);

	@Test
	@DisplayName("#92-S1 다음 페이지가 있을 때만 마지막으로 반환한 신고 ID를 다음 커서로 쓴다")
	void derivesCursorFromLastReturnedReport() {
		var query = new ListReportsQuery(Status.PENDING, 20, null);
		var firstItems = LongStream.iterate(21, id -> id - 1).limit(20)
				.mapToObj(ListReportsServiceTest::pendingReport).toList();
		when(reports.find(query)).thenReturn(new Match(firstItems, true));
		when(reports.find(new ListReportsQuery(Status.PENDING, 20, 2L)))
				.thenReturn(new Match(List.of(pendingReport(1)), false));

		var first = service.list(query);
		assertThat(first.items()).containsExactlyElementsOf(firstItems);
		assertThat(first.nextCursor()).isEqualTo(2L);

		var last = service.list(new ListReportsQuery(Status.PENDING, 20, first.nextCursor()));
		assertThat(last.items()).extracting(ListReportItemResult::reportId).containsExactly(1L);
		assertThat(last.nextCursor()).isNull();
	}

	@Test
	@DisplayName("#92-S1 선택한 상태와 커서에 해당하는 신고가 없으면 빈 페이지로 끝낸다")
	void endsOnEmptyResult() {
		var query = new ListReportsQuery(Status.DISMISSED, 50, 15L);
		when(reports.find(query)).thenReturn(new Match(List.of(), false));

		var page = service.list(query);
		assertThat(page.items()).isEmpty();
		assertThat(page.nextCursor()).isNull();
	}

	private static ListReportItemResult pendingReport(long id) {
		return new ListReportItemResult(id, 201L, Status.PENDING, ReasonType.PRIVACY, Instant.EPOCH,
				"신고 당시 제목", PostState.VISIBLE);
	}
}
