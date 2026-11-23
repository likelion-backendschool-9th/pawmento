package com.pawmento.question.adapter.out.persistence;

import com.pawmento.question.domain.QuestionMode;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

interface QuestionJpaRepository extends JpaRepository<QuestionJpaEntity, Long> {
  List<QuestionJpaEntity> findByModeAndEnabledTrueOrderByIdAsc(QuestionMode mode);
  String FILTER = " from QuestionJpaEntity q where (:mode is null or q.mode = :mode)"
      + " and (:enabled is null or q.enabled = :enabled)"
      + " and lower(q.content) like :pattern escape '!'";

  @Query("select q" + FILTER + " order by q.id desc")
  List<QuestionJpaEntity> findAdmin(@Param("mode") QuestionMode mode,
      @Param("enabled") Boolean enabled, @Param("pattern") String pattern, Pageable pageable);

  @Query("select count(q)" + FILTER)
  long countAdmin(@Param("mode") QuestionMode mode, @Param("enabled") Boolean enabled,
      @Param("pattern") String pattern);
}
