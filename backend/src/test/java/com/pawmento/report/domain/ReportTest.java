package com.pawmento.report.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.pawmento.global.exception.PermissionDeniedException;
import com.pawmento.global.exception.RuleViolationException;
import com.pawmento.report.domain.exception.InvalidReportException;
import com.pawmento.report.domain.exception.OwnTargetReportException;

class ReportTest {

	private static final Long AUTHOR = 2L;
	private static final Long REPORTER = 1L;
	private static final ReportTarget POST =
		new ReportTarget(TargetType.POST, 7L, AUTHOR, 2L, "산책 후기", "공원 한 바퀴", "LIFE");

	private static Report receive(String reasonType, String detail) {
		return Report.receive(POST, REPORTER, reasonType, detail);
	}

	@Test
	@DisplayName("#112-S1 접수하면 대상 종류·id·작성자와 읽은 글의 제목·본문·카테고리·버전을 스냅샷으로 담는다")
	void capturesSnapshot() {
		Report report = receive("SPAM", null);

		assertThat(report.getTargetType()).isEqualTo(TargetType.POST);
		assertThat(report.getTargetId()).isEqualTo(7L);
		assertThat(report.getTargetAuthorId()).isEqualTo(AUTHOR);
		assertThat(report.getReporterId()).isEqualTo(REPORTER);
		assertThat(report.getReason()).isEqualTo(ReportReason.SPAM);
		assertThat(report.getReasonDetail()).isNull();
		assertThat(report.getSnapshotTitle()).isEqualTo("산책 후기");
		assertThat(report.getSnapshotBody()).isEqualTo("공원 한 바퀴");
		assertThat(report.getSnapshotCategoryCode()).isEqualTo("LIFE");
		assertThat(report.getSnapshotTargetVersion()).isEqualTo(2L);
	}

	@Test
	@DisplayName("#112-S6 작성자는 자기 글을 신고할 수 없다")
	void rejectsOwnTarget() {
		assertThatThrownBy(() -> Report.receive(POST, AUTHOR, "SPAM", null))
			.isInstanceOf(OwnTargetReportException.class)
			.isInstanceOf(PermissionDeniedException.class)
			.hasMessage("내 글은 신고할 수 없어요.");
	}

	@Test
	@DisplayName("#112-S8 기타 신고는 앞뒤 공백을 지운 설명을 담는다")
	void otherStripsDetail() {
		assertThat(receive("OTHER", "  사진 속 차량 번호가 보여요  ").getReasonDetail()).isEqualTo("사진 속 차량 번호가 보여요");
	}

	@Test
	@DisplayName("#112-S9 유형이 없거나 대문자 이름이 아니면 거절한다")
	void rejectsUnknownReason() {
		for (String type : new String[] {null, "", "spam", "UNKNOWN", "1"}) {
			assertThatThrownBy(() -> receive(type, null)).as("유형 %s", type)
				.isInstanceOf(InvalidReportException.class)
				.isInstanceOf(RuleViolationException.class);
		}
	}

	@Test
	@DisplayName("#112-S9 기타의 설명이 없거나 공백·줄바꿈·BOM만 있으면 거절한다")
	void rejectsBlankOtherDetail() {
		for (String detail : new String[] {null, "", "   ", "\n\t", "﻿", " 　"}) {
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
	@DisplayName("#112-S9 기타의 설명에 DB에 저장할 수 없는 NUL 문자가 있으면 거절한다")
	void rejectsNulInDetail() {
		assertThatThrownBy(() -> receive("OTHER", "차량\u0000번호")).isInstanceOf(InvalidReportException.class)
			.hasMessage("설명에 쓸 수 없는 문자가 있어요.");
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
	@DisplayName("#112-S14 대상 종류는 대문자 POST만 받는다")
	void targetTypeOnlyPost() {
		assertThat(TargetType.from("POST")).isEqualTo(TargetType.POST);
		for (String type : new String[] {null, "", "post", "COMMENT", "UNKNOWN"}) {
			assertThatThrownBy(() -> TargetType.from(type)).as("대상 %s", type)
				.isInstanceOf(InvalidReportException.class)
				.hasMessage("신고 대상을 선택해 주세요.");
		}
	}

	@Test
	@DisplayName("#112-S14 대상 id가 없거나 0 이하이면 대상을 만들 수 없다")
	void targetIdPositive() {
		for (Long id : new Long[] {null, 0L, -1L}) {
			assertThatThrownBy(() -> new ReportTarget(TargetType.POST, id, AUTHOR, 2L, "제목", "본문", "LIFE"))
				.as("id %s", id)
				.isInstanceOf(InvalidReportException.class);
		}
	}

	@Test
	@DisplayName("#112-S1 글 대상은 제목·본문·카테고리 중 하나라도 없으면 만들 수 없다")
	void postTargetNeedsSnapshot() {
		String[][] cases = { {null, "본문", "LIFE"}, {"제목", null, "LIFE"}, {"제목", "본문", null} };
		for (String[] c : cases) {
			assertThatThrownBy(() -> new ReportTarget(TargetType.POST, 7L, AUTHOR, 2L, c[0], c[1], c[2]))
				.as("제목 %s, 본문 %s, 카테고리 %s", c[0], c[1], c[2])
				.isInstanceOf(InvalidReportException.class);
		}
	}

	@Test
	@DisplayName("신고할 대상과 신고한 회원이 없으면 거절한다")
	void rejectsMissingTarget() {
		assertThatThrownBy(() -> Report.receive(null, REPORTER, "SPAM", null))
			.isInstanceOf(InvalidReportException.class);
		assertThatThrownBy(() -> Report.receive(POST, null, "SPAM", null)).isInstanceOf(InvalidReportException.class);
		assertThatThrownBy(() -> new ReportTarget(TargetType.POST, 7L, null, 2L, "제목", "본문", "LIFE"))
			.isInstanceOf(InvalidReportException.class);
		assertThatThrownBy(() -> new ReportTarget(TargetType.POST, 7L, AUTHOR, null, "제목", "본문", "LIFE"))
			.isInstanceOf(InvalidReportException.class);
	}
}
