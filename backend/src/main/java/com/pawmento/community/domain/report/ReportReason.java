package com.pawmento.community.domain.report;

import java.util.Arrays;

import com.pawmento.community.domain.report.exception.InvalidReportException;

// 신고 유형. 기타(OTHER)만 설명을 받는다
public enum ReportReason {
	SPAM,     // 광고·홍보
	ABUSE,    // 욕설·비방
	HARMFUL,  // 학대·불쾌한 내용
	MISINFO,  // 잘못된 건강·돌봄 정보
	PRIVACY,  // 개인정보 노출
	OTHER;    // 기타

	// 대문자 이름만 받는다. 없거나 모르는 값(spam, UNKNOWN)은 거절한다
	public static ReportReason from(String name) {
		return Arrays.stream(values())
			.filter(reason -> reason.name().equals(name))
			.findFirst()
			.orElseThrow(() -> new InvalidReportException("신고 유형을 선택해 주세요."));
	}

	public boolean takesDetail() {
		return this == OTHER;
	}
}
