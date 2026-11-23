package com.pawmento.community.adapter.in.web.report;

import com.pawmento.community.adapter.in.web.report.exception.InvalidReportIdException;
import com.pawmento.community.adapter.in.web.report.exception.InvalidReportProcessingRequestException;
import com.pawmento.community.application.port.in.report.ProcessReportCommand;
import com.pawmento.community.application.port.in.report.ProcessReportCommand.Resolution;

import tools.jackson.databind.JsonNode;

record ProcessReportRequest(String resolution, JsonNode version, String memo) {
	ProcessReportCommand toCommand(long reportId) {
		if (reportId <= 0) {
			throw new InvalidReportIdException();
		}
		return new ProcessReportCommand(reportId, parseResolution(), parseVersion(), memo);
	}

	private Resolution parseResolution() {
		if ("RESOLVED".equals(resolution)) {
			return Resolution.RESOLVED;
		}
		if ("DISMISSED".equals(resolution)) {
			return Resolution.DISMISSED;
		}
		throw new InvalidReportProcessingRequestException("resolution은 RESOLVED, DISMISSED 중 하나여야 해요.");
	}

	private long parseVersion() {
		if (version == null || !version.isIntegralNumber() || !version.canConvertToLong()
				|| version.longValue() < 0) {
			throw new InvalidReportProcessingRequestException("version은 0 이상의 정수여야 해요.");
		}
		return version.longValue();
	}
}
