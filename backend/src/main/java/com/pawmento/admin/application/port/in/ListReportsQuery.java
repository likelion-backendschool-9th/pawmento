package com.pawmento.admin.application.port.in;

public record ListReportsQuery(ReportStatus status, int size, Long beforeReportId) {
}
