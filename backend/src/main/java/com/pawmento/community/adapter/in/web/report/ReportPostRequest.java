package com.pawmento.community.adapter.in.web.report;

// 유형과 기타 설명의 규칙은 도메인(Report, ReportReason)이 검사한다. 대문자가 아닌 유형도 거기서 400이 된다
public record ReportPostRequest(String reasonType, String reasonDetail) {
}
