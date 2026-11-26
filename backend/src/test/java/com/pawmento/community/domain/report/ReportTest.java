package com.pawmento.community.domain.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.pawmento.community.domain.report.exception.InvalidReportException;
import com.pawmento.global.exception.RuleViolationException;

class ReportTest {

	private static final Long POST = 7L;
	private static final Long REPORTER = 1L;

	@Test
	@DisplayName("#112-S8 기타 신고는 앞뒤 공백을 지운 설명을 저장한다")
	void otherStripsDetail() {
		Report report = Report.create(POST, REPORTER, ReportReason.OTHER, "  사진 속 차량 번호가 보여요  ");

		assertThat(report.getPostId()).isEqualTo(POST);
		assertThat(report.getReporterId()).isEqualTo(REPORTER);
		assertThat(report.getReason()).isEqualTo(ReportReason.OTHER);
		assertThat(report.getReasonDetail()).isEqualTo("사진 속 차량 번호가 보여요");
	}

	@Test
	@DisplayName("#112-S8 기타가 아닌 유형은 설명 없이 신고한다")
	void nonOtherHasNoDetail() {
		Report report = Report.create(POST, REPORTER, ReportReason.SPAM, null);

		assertThat(report.getReason()).isEqualTo(ReportReason.SPAM);
		assertThat(report.getReasonDetail()).isNull();
	}

	@Test
	@DisplayName("#112-S9 유형이 없으면 거절한다")
	void rejectsMissingReason() {
		assertThatThrownBy(() -> Report.create(POST, REPORTER, null, null))
			.isInstanceOf(InvalidReportException.class)
			.isInstanceOf(RuleViolationException.class);
	}

	@Test
	@DisplayName("#112-S9 기타의 설명이 없거나 공백·줄바꿈·BOM만 있으면 거절한다")
	void rejectsBlankOtherDetail() {
		for (String detail : new String[] {null, "", "   ", "\n\t", "﻿", " 　"}) {
			assertThatThrownBy(() -> Report.create(POST, REPORTER, ReportReason.OTHER, detail))
				.as("설명 %s", detail == null ? "null" : detail.codePoints().boxed().toList())
				.isInstanceOf(InvalidReportException.class);
		}
	}

	@Test
	@DisplayName("#112-S9 기타의 설명은 공백을 지운 뒤 1~200자다")
	void otherDetailLength() {
		assertThat(Report.create(POST, REPORTER, ReportReason.OTHER, "가").getReasonDetail()).hasSize(1);
		assertThat(Report.create(POST, REPORTER, ReportReason.OTHER, " " + "가".repeat(200) + " ").getReasonDetail())
			.hasSize(200);
		assertThatThrownBy(() -> Report.create(POST, REPORTER, ReportReason.OTHER, "가".repeat(201)))
			.isInstanceOf(InvalidReportException.class);
	}

	@Test
	@DisplayName("#112-S9 기타가 아닌 유형에 설명을 보내면 빈 문자열이어도 거절한다")
	void rejectsDetailOnNonOther() {
		for (ReportReason reason : ReportReason.values()) {
			if (reason.takesDetail()) {
				continue;
			}
			assertThatThrownBy(() -> Report.create(POST, REPORTER, reason, "광고예요"))
				.as(reason.name()).isInstanceOf(InvalidReportException.class);
			assertThatThrownBy(() -> Report.create(POST, REPORTER, reason, ""))
				.as(reason.name()).isInstanceOf(InvalidReportException.class);
		}
	}

	@Test
	@DisplayName("신고할 글과 신고한 회원이 없으면 거절한다")
	void rejectsMissingIds() {
		assertThatThrownBy(() -> Report.create(null, REPORTER, ReportReason.SPAM, null))
			.isInstanceOf(InvalidReportException.class);
		assertThatThrownBy(() -> Report.create(POST, null, ReportReason.SPAM, null))
			.isInstanceOf(InvalidReportException.class);
	}
}
