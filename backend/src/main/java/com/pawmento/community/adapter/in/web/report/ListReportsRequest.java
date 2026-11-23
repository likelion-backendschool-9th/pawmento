package com.pawmento.community.adapter.in.web.report;

import com.pawmento.community.adapter.in.web.report.exception.InvalidReportListRequestException;
import com.pawmento.community.adapter.in.web.report.exception.InvalidReportListRequestException.Field;
import com.pawmento.community.application.port.in.report.ListReportsQuery;
import com.pawmento.community.application.port.in.report.ReportStatus;

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
			throw new InvalidReportListRequestException(Field.STATUS, cause);
		}
	}

	private static int parseSize(String value) {
		if (value == null) {
			return DEFAULT_SIZE;
		}
		long parsed = parsePositiveDecimal(value, Field.SIZE);
		if (parsed > MAX_SIZE) {
			throw new InvalidReportListRequestException(Field.SIZE);
		}
		return (int) parsed;
	}

	private static Long parseCursor(String value) {
		return value == null ? null : parsePositiveDecimal(value, Field.CURSOR);
	}

	private static long parsePositiveDecimal(String value, Field field) {
		if (!value.matches("[0-9]+")) {
			throw new InvalidReportListRequestException(field);
		}
		long parsed;
		try {
			parsed = Long.parseLong(value);
		} catch (NumberFormatException cause) {
			throw new InvalidReportListRequestException(field, cause);
		}
		if (parsed == 0) {
			throw new InvalidReportListRequestException(field);
		}
		return parsed;
	}
}
