package com.pawmento.community.adapter.out.persistence.report;

import java.time.Instant;

import com.pawmento.community.domain.Post;
import com.pawmento.community.domain.report.PreviousReport;
import com.pawmento.community.domain.report.Report;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 글은 연관관계 대신 id로 가리킨다. FK와 부분 고유 인덱스는 db/community-report-schema.sql이 만든다
@Entity
@Table(name = "post_reports")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class PostReportJpaEntity {

	// 같은 회원의 같은 글 PENDING 신고는 하나다. 위반하면 DuplicateReportException으로 바꾼다
	static final String PENDING_UNIQUE_INDEX = "ux_post_reports_pending";

	static final String PENDING = "PENDING";
	static final String DISMISSED = "DISMISSED";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, updatable = false)
	private Long postId;

	// 탈퇴 회원의 하드 삭제를 막지 않도록 member를 FK로 참조하지 않는다
	@Column(nullable = false, updatable = false)
	private Long reporterId;

	// PENDING, RESOLVED, DISMISSED
	@Column(nullable = false, length = 20)
	private String status;

	@Column(nullable = false, updatable = false, length = 20)
	private String reasonType;

	// 기타(OTHER)만 값이 있다. 스냅샷 정리 작업이 지울 수 있다
	@Column(length = Report.DETAIL_MAX_LENGTH)
	private String reasonDetail;

	// 정리 작업이 지울 수 있어 null을 허용한다
	@Column(length = Post.TITLE_MAX_LENGTH)
	private String snapshotTitle;

	@Column(length = Post.BODY_MAX_LENGTH)
	private String snapshotBody;

	@Column(nullable = false, updatable = false, length = 30)
	private String snapshotCategoryCode;

	@Column(nullable = false, updatable = false)
	private Long snapshotPostVersion;

	// 정리 작업 전까지 늘 null이다
	private Instant snapshotClearedAt;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	// 처리·기각 단계가 쓴다
	@Version
	private Long version;

	static PostReportJpaEntity pending(Report report, Instant now) {
		PostReportJpaEntity entity = new PostReportJpaEntity();
		entity.postId = report.getPostId();
		entity.reporterId = report.getReporterId();
		entity.status = PENDING;
		entity.reasonType = report.getReason().name();
		entity.reasonDetail = report.getReasonDetail();
		entity.snapshotTitle = report.getSnapshotTitle();
		entity.snapshotBody = report.getSnapshotBody();
		entity.snapshotCategoryCode = report.getSnapshotCategoryCode();
		entity.snapshotPostVersion = report.getSnapshotPostVersion();
		entity.createdAt = now;
		return entity;
	}

	PreviousReport toPrevious() {
		return new PreviousReport(id, DISMISSED.equals(status), snapshotPostVersion);
	}

}
