package com.pawmento.community.application.service.report;

import com.pawmento.community.application.port.in.report.ListReportItemResult.PostState;
import com.pawmento.community.application.port.in.report.ListReportItemResult.ReasonType;
import com.pawmento.community.application.port.in.report.ReportDetailResult;
import com.pawmento.community.application.port.in.report.ReportStatus;
import com.pawmento.community.application.port.out.report.FindReportDetailPort.Detail;

final class ReportDetails {
	private ReportDetails() {
	}

	static ReportDetailResult toResult(Detail detail) {
		var snapshot = detail.snapshot();
		var target = detail.target();
		var processing = detail.processing();
		return new ReportDetailResult(detail.reportId(), detail.postId(), ReportStatus.valueOf(detail.status()),
				ReasonType.valueOf(detail.reasonType()), detail.reasonDetail(), detail.createdAt(), detail.version(),
				snapshot == null ? null : new ReportDetailResult.Snapshot(snapshot.title(), snapshot.body(),
						snapshot.categoryCode(), snapshot.capturedAt(), snapshot.postVersion()),
				new ReportDetailResult.Target(PostState.valueOf(target.state()), target.postVersion()),
				processing == null ? null : new ReportDetailResult.Processing(processing.processedBy(),
						processing.processedAt(), processing.memo()));
	}
}
