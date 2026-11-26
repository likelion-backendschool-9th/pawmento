package com.pawmento.community.domain.report;

// 신고 유형. 기타(OTHER)만 설명을 받는다
public enum ReportReason {
	SPAM,     // 광고·홍보
	ABUSE,    // 욕설·비방
	HARMFUL,  // 학대·불쾌한 내용
	MISINFO,  // 잘못된 건강·돌봄 정보
	PRIVACY,  // 개인정보 노출
	OTHER;    // 기타

	public boolean takesDetail() {
		return this == OTHER;
	}
}
