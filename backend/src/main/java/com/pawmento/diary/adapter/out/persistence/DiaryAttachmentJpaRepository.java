package com.pawmento.diary.adapter.out.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

interface DiaryAttachmentJpaRepository
    extends JpaRepository<DiaryAttachmentJpaEntity, DiaryAttachmentId> {
  @Query(
      "select a from DiaryAttachmentJpaEntity a where a.id.diaryId in :ids order by"
          + " a.id.diaryId,a.sortOrder")
  List<DiaryAttachmentJpaEntity> findForParents(@Param("ids") List<Long> ids);

  void deleteById_DiaryId(Long parentId);

  boolean existsById_AssetId(Long assetId);
}
