package com.pawmento.community.adapter.out.persistence.report;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import com.pawmento.community.application.exception.DuplicateReportException;
import com.pawmento.community.application.port.out.report.LoadLatestReportPort;
import com.pawmento.community.application.port.out.report.SaveReportPort;
import com.pawmento.community.domain.report.PreviousReport;
import com.pawmento.community.domain.report.Report;

import lombok.RequiredArgsConstructor;

// post_reports 테이블을 맡는다. 운영 목록 포트(FindReportPagePort)도 같은 테이블이라 이 어댑터가 구현한다
@Component
@RequiredArgsConstructor
class ReportPersistenceAdapter implements SaveReportPort, LoadLatestReportPort {

	private final PostReportJpaRepository postReportJpaRepository;
	private final Clock clock;

	@Override
	public Long save(Report report) {
		try {
			// flush해야 부분 고유 인덱스 위반이 커밋 때가 아니라 여기서 난다
			return postReportJpaRepository.saveAndFlush(PostReportJpaEntity.pending(report, now())).getId();
		}
		catch (DataIntegrityViolationException e) {
			// 중복 확인 뒤 같은 회원의 같은 글 신고가 먼저 저장됐다. 다른 제약 위반은 그대로 올린다
			if (isViolation(e, PostReportJpaEntity.PENDING_UNIQUE_INDEX)) {
				throw new DuplicateReportException(e);
			}
			throw e;
		}
	}

	@Override
	public Optional<PreviousReport> findLatest(Long postId, Long reporterId) {
		return postReportJpaRepository.findFirstByPostIdAndReporterIdOrderByIdDesc(postId, reporterId)
			.map(PostReportJpaEntity::toPrevious);
	}

	private static boolean isViolation(DataIntegrityViolationException e, String constraintName) {
		return e.getCause() instanceof ConstraintViolationException violation
				&& constraintName.equalsIgnoreCase(violation.getConstraintName());
	}

	// DB는 마이크로초까지 저장한다
	private Instant now() {
		return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
	}

}
