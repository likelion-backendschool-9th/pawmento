package com.pawmento.admin.adapter.in.web;

import com.pawmento.admin.adapter.in.web.exception.InvalidReportListRequestException;
import com.pawmento.admin.application.port.in.ListReportsQuery;
import com.pawmento.admin.application.port.in.ReportStatus;

record ListReportsRequest(String status, String size, String cursor) {
	private static final int DEFAULT_SIZE = 20;
	private static final int MAX_SIZE = 50;

	ListReportsQuery toQuery() {
		return new ListReportsQuery(parseStatus(status), parseSize(size), parseCursor(cursor));
	}

	private static ReportStatus parseStatus(String value) {
		if (value == null) {
			return ReportStatus.PENDING;
		}
		try {
			return ReportStatus.valueOf(value);
		} catch (IllegalArgumentException cause) {
			throw new InvalidReportListRequestException("status는 PENDING, RESOLVED, DISMISSED 중 하나여야 해요.", cause);
		}
	}

	private static int parseSize(String value) {
		if (value == null) {
			return DEFAULT_SIZE;
		}
		long parsed = parsePositiveDecimal(value, "size는 1 이상 50 이하의 정수여야 해요.");
		if (parsed > MAX_SIZE) {
			throw new InvalidReportListRequestException("size는 1 이상 50 이하의 정수여야 해요.");
		}
		return (int) parsed;
	}

	private static Long parseCursor(String value) {
		return value == null ? null : parsePositiveDecimal(value, "cursor는 양의 정수 신고 ID여야 해요.");
	}

	private static long parsePositiveDecimal(String value, String message) {
		if (!value.matches("[0-9]+")) {
			throw new InvalidReportListRequestException(message);
		}
		long parsed;
		try {
			parsed = Long.parseLong(value);
		} catch (NumberFormatException cause) {
			throw new InvalidReportListRequestException(message, cause);
		}
		if (parsed == 0) {
			throw new InvalidReportListRequestException(message);
		}
		return parsed;
	}
}
