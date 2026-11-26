package com.pawmento.community.domain.report;

import com.pawmento.community.domain.Post;
import com.pawmento.community.domain.TextTrim;
import com.pawmento.community.domain.report.exception.InvalidReportException;
import com.pawmento.community.domain.report.exception.OwnPostReportException;

import lombok.Getter;

// 게시글 신고. Spring·JPA를 모른다. 신고 사유의 입력 규칙과 본인 글 확인을 여기서 지킨다.
// 접수할 때 읽은 글의 제목·본문·카테고리·버전을 스냅샷으로 함께 담는다. 글이 나중에 바뀌어도 스냅샷은 그대로다
@Getter
public class Report {

	public static final int DETAIL_MAX_LENGTH = 200;

	private final Long postId;
	private final Long reporterId;
	private final ReportReason reason;
	// 기타(OTHER)일 때만 값이 있다. 앞뒤 공백을 지운 값이다
	private final String reasonDetail;
	private final String snapshotTitle;
	private final String snapshotBody;
	private final String snapshotCategoryCode;
	private final Long snapshotPostVersion;

	private Report(Post post, Long reporterId, ReportReason reason, String reasonDetail) {
		this.postId = post.getId();
		this.reporterId = reporterId;
		this.reason = reason;
		this.reasonDetail = reasonDetail;
		this.snapshotTitle = post.getTitle();
		this.snapshotBody = post.getBody();
		this.snapshotCategoryCode = post.getCategory().getCode();
		this.snapshotPostVersion = post.getVersion();
	}

	// post는 접수 트랜잭션에서 한 번 읽은 공개 글이다
	public static Report receive(Post post, Long reporterId, String reasonType, String reasonDetail) {
		if (post == null || post.getId() == null) {
			throw new InvalidReportException("신고할 글이 필요합니다.");
		}
		if (reporterId == null) {
			throw new InvalidReportException("신고한 회원이 필요합니다.");
		}
		if (post.writtenBy(reporterId)) {
			throw new OwnPostReportException();
		}
		ReportReason reason = ReportReason.from(reasonType);
		return new Report(post, reporterId, reason, requireDetail(reason, reasonDetail));
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
