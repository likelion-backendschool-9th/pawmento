package com.pawmento.question.adapter.out.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

interface QuestionAnswerJpaRepository extends JpaRepository<QuestionAnswerJpaEntity, Long> {
  @EntityGraph(attributePaths = "prompt")
  Optional<QuestionAnswerJpaEntity> findByIdAndPetId(Long id, Long petId);

  boolean existsByPrompt_IdAndAuthorMemberId(Long promptId, Long authorMemberId);

  boolean existsByPetId(Long petId);

  // PostgreSQL OFFSET accepts long offsets, including valid pages beyond JPA's int limit.
  String FILTER =
      " where pet_id=:petId"
          + " and (cast(:mode as varchar) is null or mode=:mode)"
          + " and (cast(:promptId as bigint) is null or prompt_id=:promptId)"
          + " and (cast(:author as bigint) is null or author_member_id=:author)"
          + " and (cast(:fromDate as date) is null or answered_date>=:fromDate)"
          + " and (cast(:toDate as date) is null or answered_date<=:toDate)";

  @Query(
      value =
          "select id from question_answer"
              + FILTER
              + " order by answered_at desc,id desc limit :pageSize offset :offsetRows",
      nativeQuery = true)
  List<Long> findPageIds(
      @Param("petId") Long petId,
      @Param("mode") String mode,
      @Param("promptId") Long promptId,
      @Param("author") Long author,
      @Param("fromDate") LocalDate from,
      @Param("toDate") LocalDate to,
      @Param("pageSize") int size,
      @Param("offsetRows") long offset);

  @EntityGraph(attributePaths = "prompt")
  List<QuestionAnswerJpaEntity> findByIdInOrderByAnsweredAtDescIdDesc(List<Long> ids);

  @Query(value = "select count(*) from question_answer" + FILTER, nativeQuery = true)
  long countEntries(
      @Param("petId") Long petId,
      @Param("mode") String mode,
      @Param("promptId") Long promptId,
      @Param("author") Long author,
      @Param("fromDate") LocalDate from,
      @Param("toDate") LocalDate to);
}
