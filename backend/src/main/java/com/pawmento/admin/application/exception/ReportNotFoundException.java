package com.pawmento.admin.application.exception;

import com.pawmento.global.exception.ResourceNotFoundException;

public class ReportNotFoundException extends ResourceNotFoundException {
	public ReportNotFoundException() {
		super("신고를 찾을 수 없어요.");
	}
}
