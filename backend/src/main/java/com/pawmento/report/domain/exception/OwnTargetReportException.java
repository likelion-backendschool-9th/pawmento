package com.pawmento.report.domain.exception;

import com.pawmento.global.exception.PermissionDeniedException;

// 작성자가 자기 글을 신고하려 했다
public class OwnTargetReportException extends PermissionDeniedException {
	public OwnTargetReportException() {
		super("내 글은 신고할 수 없어요.");
	}
}
