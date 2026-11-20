package com.pawmento.question.adapter.out.persistence;

import com.pawmento.question.domain.QuestionMode;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface QuestionJpaRepository extends JpaRepository<QuestionJpaEntity, Long> {
  List<QuestionJpaEntity> findByModeAndEnabledTrueOrderByIdAsc(QuestionMode mode);
}
