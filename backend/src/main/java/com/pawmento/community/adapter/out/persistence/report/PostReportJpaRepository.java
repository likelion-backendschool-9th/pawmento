package com.pawmento.community.adapter.out.persistence.report;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface PostReportJpaRepository extends JpaRepository<PostReportJpaEntity, Long> {

	Optional<PostReportJpaEntity> findFirstByPostIdAndReporterIdOrderByIdDesc(Long postId, Long reporterId);

}
