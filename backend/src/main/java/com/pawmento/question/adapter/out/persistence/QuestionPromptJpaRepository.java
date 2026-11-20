package com.pawmento.question.adapter.out.persistence;

import com.pawmento.question.domain.QuestionMode;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface QuestionPromptJpaRepository extends JpaRepository<QuestionPromptJpaEntity, Long> {
  @Query(
      "select p from QuestionPromptJpaEntity p, QuestionProgressJpaEntity g where g.id.petId=:petId"
          + " and g.id.mode=:mode and p.id=g.currentPromptId")
  Optional<QuestionPromptJpaEntity> findCurrent(
      @Param("petId") Long petId, @Param("mode") QuestionMode mode);

  Optional<QuestionPromptJpaEntity> findFirstByPetIdAndModeOrderBySequenceDesc(
      Long petId, QuestionMode mode);
}
