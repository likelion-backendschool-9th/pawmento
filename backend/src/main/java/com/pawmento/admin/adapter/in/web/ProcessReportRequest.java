package com.pawmento.admin.adapter.in.web;

import com.pawmento.admin.adapter.in.web.exception.InvalidReportIdException;
import com.pawmento.admin.adapter.in.web.exception.InvalidReportProcessingRequestException;
import com.pawmento.admin.application.port.in.ProcessReportCommand;
import com.pawmento.admin.application.port.in.ProcessReportCommand.Resolution;

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
