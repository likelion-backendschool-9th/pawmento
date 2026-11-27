package com.pawmento.report.domain;

import java.util.Arrays;

import com.pawmento.report.domain.exception.InvalidReportException;

// 신고 대상 종류. 댓글이 생기면 COMMENT를 더한다
public enum TargetType {
	POST;

	// 대문자 이름만 받는다. 없거나 모르는 값(post, COMMENT, UNKNOWN)은 거절한다
	public static TargetType from(String name) {
		return Arrays.stream(values())
			.filter(type -> type.name().equals(name))
			.findFirst()
			.orElseThrow(() -> new InvalidReportException("신고 대상을 선택해 주세요."));
	}
}
