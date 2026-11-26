package com.pawmento.community.domain.report;

// 같은 회원이 같은 글에 남긴 가장 최근 신고. 다시 신고할 수 있는지 판정할 때 쓴다
public record PreviousReport(Long reportId, boolean dismissed, Long postVersion) {
}
