package com.pawmento.community.domain.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.pawmento.community.domain.Post;
import com.pawmento.community.domain.PostCategory;
import com.pawmento.community.domain.report.exception.InvalidReportException;
import com.pawmento.community.domain.report.exception.OwnPostReportException;
import com.pawmento.global.exception.PermissionDeniedException;
import com.pawmento.global.exception.RuleViolationException;

class ReportTest {

	private static final Long AUTHOR = 2L;
	private static final Long REPORTER = 1L;
	private static final PostCategory LIFE = PostCategory.restore(1L, "LIFE", "반려 생활", 1, true);
	private static final Post POST = Post.restore(7L, AUTHOR, LIFE, "산책 후기", "공원 한 바퀴", null, null, false, 2L);

	private static Report receive(String reasonType, String detail) {
		return Report.receive(POST, REPORTER, reasonType, detail);
	}

	@Test
	@DisplayName("#112-S1 접수하면 읽은 글의 제목·본문·카테고리·버전을 스냅샷으로 담는다")
	void capturesSnapshot() {
		Report report = receive("SPAM", null);

		assertThat(report.getPostId()).isEqualTo(7L);
		assertThat(report.getReporterId()).isEqualTo(REPORTER);
		assertThat(report.getReason()).isEqualTo(ReportReason.SPAM);
		assertThat(report.getReasonDetail()).isNull();
		assertThat(report.getSnapshotTitle()).isEqualTo("산책 후기");
		assertThat(report.getSnapshotBody()).isEqualTo("공원 한 바퀴");
		assertThat(report.getSnapshotCategoryCode()).isEqualTo("LIFE");
		assertThat(report.getSnapshotPostVersion()).isEqualTo(2L);
	}

	@Test
	@DisplayName("#112-S6 작성자는 자기 글을 신고할 수 없다")
	void rejectsOwnPost() {
		assertThatThrownBy(() -> Report.receive(POST, AUTHOR, "SPAM", null))
			.isInstanceOf(OwnPostReportException.class)
			.isInstanceOf(PermissionDeniedException.class);
	}

	@Test
	@DisplayName("#112-S8 기타 신고는 앞뒤 공백을 지운 설명을 담는다")
	void otherStripsDetail() {
		assertThat(receive("OTHER", "  사진 속 차량 번호가 보여요  ").getReasonDetail()).isEqualTo("사진 속 차량 번호가 보여요");
	}

	@Test
	@DisplayName("#112-S9 유형이 없거나 대문자 이름이 아니면 거절한다")
	void rejectsUnknownReason() {
		for (String type : new String[] {null, "", "spam", "UNKNOWN"}) {
			assertThatThrownBy(() -> receive(type, null)).as("유형 %s", type)
				.isInstanceOf(InvalidReportException.class)
				.isInstanceOf(RuleViolationException.class);
		}
	}

	@Test
	@DisplayName("#112-S9 기타의 설명이 없거나 공백·줄바꿈·BOM만 있으면 거절한다")
	void rejectsBlankOtherDetail() {
		for (String detail : new String[] {null, "", "   ", "\n\t", "﻿", " 　"}) {
			assertThatThrownBy(() -> receive("OTHER", detail))
				.as("설명 %s", detail == null ? "null" : detail.codePoints().boxed().toList())
				.isInstanceOf(InvalidReportException.class);
		}
	}

	@Test
	@DisplayName("#112-S9 기타의 설명은 공백을 지운 뒤 1~200자다")
	void otherDetailLength() {
		assertThat(receive("OTHER", "가").getReasonDetail()).hasSize(1);
		assertThat(receive("OTHER", " " + "가".repeat(200) + " ").getReasonDetail()).hasSize(200);
		assertThatThrownBy(() -> receive("OTHER", "가".repeat(201))).isInstanceOf(InvalidReportException.class);
	}

	@Test
	@DisplayName("#112-S9 기타가 아닌 유형에 설명을 보내면 빈 문자열이어도 거절한다")
	void rejectsDetailOnNonOther() {
		for (ReportReason reason : ReportReason.values()) {
			if (reason.takesDetail()) {
				continue;
			}
			assertThatThrownBy(() -> receive(reason.name(), "광고예요")).as(reason.name())
				.isInstanceOf(InvalidReportException.class);
			assertThatThrownBy(() -> receive(reason.name(), "")).as(reason.name())
				.isInstanceOf(InvalidReportException.class);
		}
	}

	@Test
	@DisplayName("신고할 글과 신고한 회원이 없으면 거절한다")
	void rejectsMissingTarget() {
		assertThatThrownBy(() -> Report.receive(null, REPORTER, "SPAM", null))
			.isInstanceOf(InvalidReportException.class);
		assertThatThrownBy(() -> Report.receive(POST, null, "SPAM", null)).isInstanceOf(InvalidReportException.class);
	}
}
