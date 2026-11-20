package com.pawmento.question.adapter.out.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

interface QuestionAttachmentJpaRepository
    extends JpaRepository<QuestionAttachmentJpaEntity, QuestionAttachmentId> {
  @Query(
      "select a from QuestionAttachmentJpaEntity a where a.id.answerId in :ids order by"
          + " a.id.answerId,a.sortOrder")
  List<QuestionAttachmentJpaEntity> findForParents(@Param("ids") List<Long> ids);

  void deleteById_AnswerId(Long parentId);

  boolean existsById_AssetId(Long assetId);
}
