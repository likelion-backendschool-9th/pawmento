package com.pawmento.question.adapter.out.persistence;

import com.pawmento.question.domain.QuestionMode;
import java.util.List;
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

  String HISTORY_FILTER = " where pet_id=:petId and (cast(:mode as varchar) is null or mode=:mode)";

  @Query(
      value =
          "select id from question_prompt"
              + HISTORY_FILTER
              + " order by assigned_at desc,id desc limit :pageSize offset :offsetRows",
      nativeQuery = true)
  List<Long> historyIds(
      @Param("petId") Long petId,
      @Param("mode") String mode,
      @Param("pageSize") int size,
      @Param("offsetRows") long offset);

  @Query(value = "select count(*) from question_prompt" + HISTORY_FILTER, nativeQuery = true)
  long historyCount(@Param("petId") Long petId, @Param("mode") String mode);

  List<QuestionPromptJpaEntity> findByIdInOrderByAssignedAtDescIdDesc(List<Long> ids);

  Optional<QuestionPromptJpaEntity> findByIdAndPetId(Long id, Long petId);

  interface AnswerSummary {
    Long getPromptId();

    long getAnswerCount();

    Long getMyAnswerId();
  }

  @Query(
      value =
          "select prompt_id as promptId, count(*) as answerCount, max(case when"
              + " author_member_id=:memberId then id end) as myAnswerId from question_answer where"
              + " pet_id=:petId and prompt_id in (:ids) group by prompt_id",
      nativeQuery = true)
  List<AnswerSummary> summarizeAnswers(
      @Param("petId") Long petId, @Param("ids") List<Long> ids, @Param("memberId") Long memberId);
}
