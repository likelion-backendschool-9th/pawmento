package com.pawmento.community.domain.report;

import com.pawmento.community.domain.TextTrim;
import com.pawmento.community.domain.report.exception.InvalidReportException;

import lombok.Getter;

// 게시글 신고. Spring·JPA를 모른다. 신고 사유의 입력 규칙을 여기서 지킨다
@Getter
public class Report {

	public static final int DETAIL_MAX_LENGTH = 200;

	private final Long postId;
	private final Long reporterId;
	private final ReportReason reason;
	// 기타(OTHER)일 때만 값이 있다. 앞뒤 공백을 지운 값이다
	private final String reasonDetail;

	private Report(Long postId, Long reporterId, ReportReason reason, String reasonDetail) {
		this.postId = postId;
		this.reporterId = reporterId;
		this.reason = reason;
		this.reasonDetail = reasonDetail;
	}

	public static Report create(Long postId, Long reporterId, ReportReason reason, String reasonDetail) {
		if (postId == null) {
			throw new InvalidReportException("신고할 글이 필요합니다.");
		}
		if (reporterId == null) {
			throw new InvalidReportException("신고한 회원이 필요합니다.");
		}
		if (reason == null) {
			throw new InvalidReportException("신고 유형을 선택해 주세요.");
		}
		return new Report(postId, reporterId, reason, requireDetail(reason, reasonDetail));
	}

	// 기타는 설명이 필수이고, 다른 유형은 설명을 보내면 거절한다(빈 문자열도 보낸 것으로 본다)
	private static String requireDetail(ReportReason reason, String detail) {
		if (!reason.takesDetail()) {
			if (detail != null) {
				throw new InvalidReportException("기타 유형만 설명을 적을 수 있어요.");
			}
			return null;
		}
		String message = "기타 사유는 1~" + DETAIL_MAX_LENGTH + "자로 입력해 주세요.";
		if (detail == null) {
			throw new InvalidReportException(message);
		}
		String stripped = TextTrim.strip(detail);
		if (stripped.isEmpty() || stripped.length() > DETAIL_MAX_LENGTH) {
			throw new InvalidReportException(message);
		}
		return stripped;
	}
}
