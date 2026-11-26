package com.pawmento.community.application.port.in.report;

// reasonType은 요청 그대로의 문자열이다. 유형 확인은 도메인(ReportReason)이 한다
public record ReportPostCommand(Long memberId, Long postId, String reasonType, String reasonDetail) {
}
