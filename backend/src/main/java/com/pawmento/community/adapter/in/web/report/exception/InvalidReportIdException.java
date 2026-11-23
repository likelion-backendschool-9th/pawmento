package com.pawmento.community.adapter.in.web.report.exception;

import com.pawmento.global.exception.RuleViolationException;

public class InvalidReportIdException extends RuleViolationException {
	public InvalidReportIdException() {
		super("유효한 신고 ID를 입력해 주세요.");
	}
}
