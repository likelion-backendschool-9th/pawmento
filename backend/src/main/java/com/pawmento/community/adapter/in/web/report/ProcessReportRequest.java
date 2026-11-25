package com.pawmento.community.adapter.in.web.report;

import com.pawmento.community.adapter.in.web.report.exception.InvalidReportProcessingRequestException;

import tools.jackson.databind.JsonNode;

record ProcessReportRequest(String resolution, JsonNode version, JsonNode memo) {
	Validated validate() {
		if (!"RESOLVED".equals(resolution) && !"DISMISSED".equals(resolution)) {
			throw new InvalidReportProcessingRequestException("resolution은 RESOLVED, DISMISSED 중 하나여야 해요.");
		}
		if (version == null || !version.isIntegralNumber() || !version.canConvertToLong()
				|| version.longValue() < 0) {
			throw new InvalidReportProcessingRequestException("version은 0 이상의 정수여야 해요.");
		}
		if (memo == null || !memo.isString()) {
			throw invalidMemo();
		}
		String normalized = trimMemo(memo.asString());
		if (normalized.isEmpty() || normalized.length() > 500) {
			throw invalidMemo();
		}
		return new Validated(resolution, version.longValue(), normalized);
	}

	private static InvalidReportProcessingRequestException invalidMemo() {
		return new InvalidReportProcessingRequestException("처리 메모는 1자 이상 500자 이하여야 해요.");
	}

	// Post와 동일한 JavaScript trim() 문자 범위를 사용한다.
	private static String trimMemo(String value) {
		int start = 0;
		int end = value.length();
		while (start < end && isTrimSpace(value.charAt(start))) {
			start++;
		}
		while (end > start && isTrimSpace(value.charAt(end - 1))) {
			end--;
		}
		return value.substring(start, end);
	}

	private static boolean isTrimSpace(char value) {
		return (value >= '\t' && value <= '\r') || value == '\uFEFF' || Character.isSpaceChar(value);
	}

	record Validated(String resolution, long version, String memo) {
	}
}
