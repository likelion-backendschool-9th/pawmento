package com.pawmento.diary.adapter.out.persistence;

import java.time.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface DiaryEntryJpaRepository extends JpaRepository<DiaryEntryJpaEntity, Long> {
  boolean existsByPetId(Long petId);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "update DiaryEntryJpaEntity e set e.content = :content, e.recordedDate = :date, e.updatedAt ="
          + " :now, e.version = e.version + 1 where e.id = :id and e.version = :version")
  int updateVersion(
      @Param("id") Long id,
      @Param("version") Long version,
      @Param("content") String content,
      @Param("date") LocalDate date,
      @Param("now") Instant now);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("delete from DiaryEntryJpaEntity e where e.id = :id and e.version = :version")
  int deleteVersion(@Param("id") Long id, @Param("version") Long version);
}
