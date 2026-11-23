package com.pawmento.community.application.port.in.report;

public record ListReportsQuery(ReportStatus status, int size, Long beforeReportId) {
}
