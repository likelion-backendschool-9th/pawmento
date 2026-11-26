package com.pawmento.report.domain;

import com.pawmento.report.domain.exception.InvalidReportException;
import com.pawmento.report.domain.exception.OwnTargetReportException;

import lombok.Getter;

// 신고. Spring·JPA와 대상 도메인을 모른다. 신고 사유의 입력 규칙과 본인 글 확인을 여기서 지킨다.
// 접수할 때 읽은 대상의 작성자·제목·본문·카테고리·버전을 함께 담는다. 대상이 나중에 바뀌어도 스냅샷은 그대로다
@Getter
public class Report {

	public static final int DETAIL_MAX_LENGTH = 200;

	private final TargetType targetType;
	private final Long targetId;
	private final Long targetAuthorId;
	private final Long reporterId;
	private final ReportReason reason;
	// 기타(OTHER)일 때만 값이 있다. 앞뒤 공백을 지운 값이다
	private final String reasonDetail;
	private final String snapshotTitle;
	private final String snapshotBody;
	private final String snapshotCategoryCode;
	private final Long snapshotTargetVersion;

	private Report(ReportTarget target, Long reporterId, ReportReason reason, String reasonDetail) {
		this.targetType = target.type();
		this.targetId = target.id();
		this.targetAuthorId = target.authorId();
		this.reporterId = reporterId;
		this.reason = reason;
		this.reasonDetail = reasonDetail;
		this.snapshotTitle = target.title();
		this.snapshotBody = target.body();
		this.snapshotCategoryCode = target.categoryCode();
		this.snapshotTargetVersion = target.version();
	}

	// target은 접수 트랜잭션에서 한 번 읽은 공개 대상이다
	public static Report receive(ReportTarget target, Long reporterId, String reasonType, String reasonDetail) {
		if (target == null) {
			throw new InvalidReportException("신고할 대상이 필요합니다.");
		}
		if (reporterId == null) {
			throw new InvalidReportException("신고한 회원이 필요합니다.");
		}
		if (target.writtenBy(reporterId)) {
			throw new OwnTargetReportException();
		}
		ReportReason reason = ReportReason.from(reasonType);
		return new Report(target, reporterId, reason, requireDetail(reason, reasonDetail));
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
		// PostgreSQL 문자열은 NUL(U+0000)을 저장하지 못한다. 저장에서 500이 나기 전에 400으로 거절한다
		if (stripped.indexOf('\u0000') >= 0) {
			throw new InvalidReportException("설명에 쓸 수 없는 문자가 있어요.");
		}
		return stripped;
	}
}
