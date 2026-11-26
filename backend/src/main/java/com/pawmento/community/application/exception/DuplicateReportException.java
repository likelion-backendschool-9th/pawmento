package com.pawmento.community.application.exception;

import com.pawmento.global.exception.StateConflictException;

// 같은 회원이 같은 글을 이미 신고했다. 요청한 회원 본인의 신고만 보고 판정한다
public class DuplicateReportException extends StateConflictException {

	private static final String MESSAGE = "이미 신고한 게시글이에요.";

	public DuplicateReportException() {
		super(MESSAGE);
	}

	// 확인과 저장 사이에 같은 신고가 먼저 저장됐다. 저장소의 제약 위반 예외를 cause로 남긴다
	public DuplicateReportException(Throwable cause) {
		super(MESSAGE, cause);
	}
}
