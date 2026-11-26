package com.pawmento.community.domain.report.exception;

import com.pawmento.global.exception.PermissionDeniedException;

// 작성자가 자기 글을 신고하려 했다
public class OwnPostReportException extends PermissionDeniedException {
	public OwnPostReportException() {
		super("내 글은 신고할 수 없어요.");
	}
}
