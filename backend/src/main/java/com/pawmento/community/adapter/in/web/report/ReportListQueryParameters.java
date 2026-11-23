package com.pawmento.community.adapter.in.web.report;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;

import com.pawmento.community.adapter.in.web.report.exception.InvalidReportListRequestException;
import com.pawmento.community.adapter.in.web.report.exception.InvalidReportListRequestException.Field;

final class ReportListQueryParameters {
	private ReportListQueryParameters() {
	}

	static ListReportsRequest parse(String query) {
		var values = new EnumMap<Field, String>(Field.class);
		if (query != null) {
			for (String pair : query.split("&", -1)) {
				var parts = pair.split("=", 2);
				Field field = field(parts[0]);
				if (field == null) {
					continue;
				}
				if (values.containsKey(field)) {
					throw new InvalidReportListRequestException(field);
				}
				try {
					values.put(field, parts.length == 2 ? decode(parts[1]) : "");
				} catch (IllegalArgumentException cause) {
					throw new InvalidReportListRequestException(field, cause);
				}
			}
		}
		return new ListReportsRequest(values.get(Field.STATUS), values.get(Field.SIZE), values.get(Field.CURSOR));
	}

	private static Field field(String raw) {
		try {
			return switch (decode(raw)) {
				case "status" -> Field.STATUS;
				case "size" -> Field.SIZE;
				case "cursor" -> Field.CURSOR;
				default -> null;
			};
		} catch (IllegalArgumentException ignored) {
			return null;
		}
	}

	private static String decode(String raw) {
		return URLDecoder.decode(raw, StandardCharsets.UTF_8);
	}
}
